import AVFoundation
import Foundation

/// Captures 16 kHz mono PCM, which is the only format the transcription models
/// take, and hands it out two ways: as a growing buffer for the batch model and
/// as 100 ms chunks for the live session.
///
/// The hardware input is whatever the device feels like (48 kHz float on every
/// current iPhone), so everything goes through an `AVAudioConverter` on the way
/// out rather than trusting the tap format.
final class AudioRecorder {

    static let sampleRate: Double = 16_000
    /// Under ~0.3 s the user almost certainly mis-tapped.
    static let minimumUsefulSamples = Int(sampleRate * 0.3)
    private static let maximumSeconds = 120.0

    private let engine = AVAudioEngine()
    private let outputFormat = AVAudioFormat(
        commonFormat: .pcmFormatInt16,
        sampleRate: AudioRecorder.sampleRate,
        channels: 1,
        interleaved: true
    )!

    private var converter: AVAudioConverter?
    private let lock = NSLock()
    private var pcm = Data()
    private var running = false

    /// Peak level of the last chunk, 0...1 — drives the mic key's pulse.
    var onLevel: ((Float) -> Void)?
    /// Called on the audio thread with each 16 kHz chunk, for the live session.
    var onChunk: ((Data) -> Void)?

    var isRecording: Bool {
        lock.lock(); defer { lock.unlock() }
        return running
    }

    var sampleCount: Int {
        lock.lock(); defer { lock.unlock() }
        return pcm.count / 2
    }

    // -- permission --------------------------------------------------------

    static func hasMicPermission() -> Bool {
        if #available(iOS 17.0, *) {
            return AVAudioApplication.shared.recordPermission == .granted
        }
        return AVAudioSession.sharedInstance().recordPermission == .granted
    }

    static func requestMicPermission() async -> Bool {
        if #available(iOS 17.0, *) {
            return await AVAudioApplication.requestRecordPermission()
        }
        return await withCheckedContinuation { continuation in
            AVAudioSession.sharedInstance().requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
    }

    // -- capture -----------------------------------------------------------

    func start() throws {
        guard !isRecording else { return }
        guard Self.hasMicPermission() else { throw DictationError.micPermission }

        let session = AVAudioSession.sharedInstance()
        do {
            // .mixWithOthers keeps music and calls from being ducked out from
            // under the user just because they tapped the mic key.
            try session.setCategory(.playAndRecord,
                                    mode: .measurement,
                                    options: [.mixWithOthers, .allowBluetooth, .defaultToSpeaker])
            try session.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            throw DictationError.micUnavailable
        }

        let input = engine.inputNode
        let inputFormat = input.outputFormat(forBus: 0)
        guard inputFormat.sampleRate > 0 else { throw DictationError.micUnavailable }
        guard let converter = AVAudioConverter(from: inputFormat, to: outputFormat) else {
            throw DictationError.micUnavailable
        }
        self.converter = converter

        lock.lock()
        pcm.removeAll(keepingCapacity: true)
        running = true
        lock.unlock()

        // 100 ms of input audio per callback, matching the Android recorder.
        let frames = AVAudioFrameCount(inputFormat.sampleRate / 10)
        input.installTap(onBus: 0, bufferSize: frames, format: inputFormat) { [weak self] buffer, _ in
            self?.append(buffer, using: converter)
        }

        engine.prepare()
        do {
            try engine.start()
        } catch {
            input.removeTap(onBus: 0)
            lock.lock(); running = false; lock.unlock()
            throw DictationError.micUnavailable
        }
    }

    /// Stops capture and hands back the whole utterance as 16 kHz mono PCM.
    @discardableResult
    func stop() -> Data {
        guard isRecording else { return Data() }

        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        converter = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)

        lock.lock()
        running = false
        let out = pcm
        pcm.removeAll(keepingCapacity: false)
        lock.unlock()

        onLevel?(0)
        return out
    }

    /// Aborts capture and throws the audio away.
    func cancel() {
        _ = stop()
    }

    private func append(_ buffer: AVAudioPCMBuffer, using converter: AVAudioConverter) {
        let ratio = outputFormat.sampleRate / buffer.format.sampleRate
        let capacity = AVAudioFrameCount(Double(buffer.frameLength) * ratio) + 1024
        guard let out = AVAudioPCMBuffer(pcmFormat: outputFormat, frameCapacity: capacity) else { return }

        var supplied = false
        var error: NSError?
        converter.convert(to: out, error: &error) { _, status in
            if supplied {
                status.pointee = .noDataNow
                return nil
            }
            supplied = true
            status.pointee = .haveData
            return buffer
        }
        guard error == nil, out.frameLength > 0, let channel = out.int16ChannelData else { return }

        let byteCount = Int(out.frameLength) * 2
        let chunk = Data(bytes: channel[0], count: byteCount)

        lock.lock()
        // Hard cap the utterance so a forgotten recording can't grow until the
        // extension is jetsammed.
        let cap = Int(Self.sampleRate * Self.maximumSeconds) * 2
        if pcm.count < cap { pcm.append(chunk) }
        lock.unlock()

        onChunk?(chunk)
        onLevel?(Self.peak(of: channel[0], frames: Int(out.frameLength)))
    }

    private static func peak(of samples: UnsafeMutablePointer<Int16>, frames: Int) -> Float {
        var maximum: Int32 = 0
        for i in 0..<frames {
            let value = Int32(samples[i]).magnitude
            if Int32(value) > maximum { maximum = Int32(value) }
        }
        return Float(maximum) / 32768.0
    }
}

extension Data {
    /// Wraps raw 16 kHz mono PCM in a WAV header, which is what the batch
    /// endpoint wants as `audio/wav`.
    func asWav(sampleRate: Int = Int(AudioRecorder.sampleRate)) -> Data {
        var header = Data()
        let channels: UInt16 = 1
        let bitsPerSample: UInt16 = 16
        let byteRate = UInt32(sampleRate * Int(channels) * Int(bitsPerSample) / 8)
        let blockAlign = UInt16(channels * bitsPerSample / 8)

        func append<T: FixedWidthInteger>(_ value: T) {
            withUnsafeBytes(of: value.littleEndian) { header.append(contentsOf: $0) }
        }

        header.append(contentsOf: Array("RIFF".utf8))
        append(UInt32(36 + count))
        header.append(contentsOf: Array("WAVE".utf8))
        header.append(contentsOf: Array("fmt ".utf8))
        append(UInt32(16))            // PCM chunk size
        append(UInt16(1))             // PCM format
        append(channels)
        append(UInt32(sampleRate))
        append(byteRate)
        append(blockAlign)
        append(bitsPerSample)
        header.append(contentsOf: Array("data".utf8))
        append(UInt32(count))

        return header + self
    }
}
