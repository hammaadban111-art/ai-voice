/* Service worker.
 *
 * Its job is to make the home-screen icon behave like an app: open instantly,
 * and open at all when the phone has no signal. Transcription still needs the
 * network — this only caches the shell.
 */
const CACHE = "voiceappv4-v1";
const SHELL = ["./", "./index.html", "./manifest.webmanifest", "./icon-192.png", "./icon-512.png"];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);
  // Never touch Gemini traffic: it must always be live, and it is a POST.
  if (event.request.method !== "GET" || url.origin !== self.location.origin) return;

  // Network first, so a new version of the app is picked up as soon as the
  // phone has signal, with the cache as the offline fallback.
  event.respondWith(
    fetch(event.request)
      .then((response) => {
        const copy = response.clone();
        caches.open(CACHE).then((cache) => cache.put(event.request, copy)).catch(() => {});
        return response;
      })
      .catch(() => caches.match(event.request).then((hit) => hit || caches.match("./index.html")))
  );
});
