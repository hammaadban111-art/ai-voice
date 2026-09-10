package com.hammaad.voiceappv4.accessibility

import android.text.InputType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldPolicyTest {
    @Test fun `allows ordinary editable text field`() = assertTrue(FieldPolicy.isAllowed(
        packageName = "com.example.chat", editable = true, password = false,
        inputType = InputType.TYPE_CLASS_TEXT, userExcluded = emptySet(),
    ))

    @Test fun `rejects password flag and password variations`() {
        assertFalse(FieldPolicy.isAllowed("com.example", true, true, InputType.TYPE_CLASS_TEXT, emptySet()))
        assertFalse(FieldPolicy.isAllowed("com.example", true, false, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, emptySet()))
        assertFalse(FieldPolicy.isAllowed("com.example", true, false, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, emptySet()))
    }

    @Test fun `rejects explicitly excluded and built-in sensitive apps`() {
        assertFalse(FieldPolicy.isAllowed("com.example.bank", true, false, InputType.TYPE_CLASS_TEXT, setOf("com.example.bank")))
        assertFalse(FieldPolicy.isAllowed("com.android.systemui", true, false, InputType.TYPE_CLASS_TEXT, emptySet()))
    }
}
