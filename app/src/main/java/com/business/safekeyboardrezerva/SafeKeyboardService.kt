@file:Suppress("DEPRECATION")

package com.business.safekeyboardrezerva

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.Log
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class SafeKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {
    private var isShifted = false
    private var isCapsLock = false
    private var lastShiftTime = 0L
    private var currentKeyboard = KEYBOARD_QWERTY

    private lateinit var keyboardView: KeyboardView
    private lateinit var qwertyKeyboard: Keyboard
    private lateinit var symbolsKeyboard: Keyboard
    private lateinit var extendedSymbolsKeyboard: Keyboard

    companion object {
        private const val KEYBOARD_QWERTY = 0
        private const val KEYBOARD_SYMBOLS = 1
        private const val KEYBOARD_EXTENDED_SYMBOLS = 2
    }

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        qwertyKeyboard = Keyboard(this, R.xml.qwerty)
        symbolsKeyboard = Keyboard(this, R.xml.symbols)
        extendedSymbolsKeyboard = Keyboard(this, R.xml.extended_symbols)

        keyboardView.keyboard = qwertyKeyboard
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.isPreviewEnabled = false
        return keyboardView
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection = currentInputConnection ?: return

        when (primaryCode) {
            -1 -> { // Shift key
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastShiftTime < 500) { // Double tap detected
                    isCapsLock = !isCapsLock
                    isShifted = isCapsLock
                } else {
                    isShifted = !isShifted
                    isCapsLock = false
                }
                lastShiftTime = currentTime

                // Update keyboard display - this line is causing the issue
                // qwertyKeyboard.isShifted = isShifted

                // Use this approach instead:
                updateShiftKeyAppearance()
                setShifted(isShifted)
                keyboardView.invalidateAllKeys()
            }
            -2 -> { // Symbol key / ABC key
                when (currentKeyboard) {
                    KEYBOARD_QWERTY -> {
                        currentKeyboard = KEYBOARD_SYMBOLS
                        keyboardView.keyboard = symbolsKeyboard
                    }
                    KEYBOARD_SYMBOLS, KEYBOARD_EXTENDED_SYMBOLS -> {
                        currentKeyboard = KEYBOARD_QWERTY
                        keyboardView.keyboard = qwertyKeyboard
                    }
                }
                keyboardView.invalidateAllKeys()
            }
            -3 -> { // More symbols key
                currentKeyboard = KEYBOARD_EXTENDED_SYMBOLS
                keyboardView.keyboard = extendedSymbolsKeyboard
                keyboardView.invalidateAllKeys()
            }
            -4 -> { // Enter key
                inputConnection.commitText("\n", 1)
//                inputConnection.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
//                inputConnection.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
            }
            -5 -> { // Delete key
                inputConnection.deleteSurroundingText(1, 0)
            }
            -6 -> { // Back to numbers (from extended symbols)
                currentKeyboard = KEYBOARD_SYMBOLS
                keyboardView.keyboard = symbolsKeyboard
                keyboardView.invalidateAllKeys()
            }
            else -> {
                var code = primaryCode.toChar()

                // Handle shift for letter keys
                if (isShifted && primaryCode in 97..122) { // 'a' to 'z'
                    code = (primaryCode - 32).toChar()
                    // If not caps lock, reset shift after one character
                    if (!isCapsLock) {
                        isShifted = false
                        setShifted(false)
                        updateShiftKeyAppearance()
                        keyboardView.invalidateAllKeys()
                    }
                }

                inputConnection.commitText(code.toString(), 1)
            }
        }
    }

    // Rest of your methods remain the same
    private fun getTextFromInput(): String {
        // This is simplistic. If needed, keep your own internal buffer of typed characters.
        return "" // Replace with actual typed string
    }

    private fun sendToApi(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://your-api-url.com/send")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val jsonBody = """{ "message": "$text" }"""
                conn.outputStream.write(jsonBody.toByteArray())

                val response = conn.inputStream.bufferedReader().readText()
                Log.d("API_RESPONSE", response)
            } catch (e: Exception) {
                Log.e("API_CALL", "Failed: ${e.message}")
            }
        }
    }

    private fun updateShiftKeyAppearance() {
        val keyboard = keyboardView.keyboard
        for (key in keyboard.keys) {
            if (key.codes[0] == -1) { // Shift key
                if (isCapsLock) {
                    // Set appearance for caps lock (bold, underlined, or filled)
                    key.label = "⇧̲" // Underlined shift arrow
                    key.icon = null // Remove any icon if using label
                } else if (isShifted) {
                    // Set appearance for shift pressed once
                    key.label = "⇧" // Regular shift arrow but we'll make it visually distinct
                    key.icon = null
                } else {
                    // Set appearance for normal state
                    key.label = "⇧"
                    key.icon = null
                }
                break
            }
        }
    }

    // Improved method to set shifted state for all keys
    private fun setShifted(shifted: Boolean) {
        val keyboard = keyboardView.keyboard
        for (key in keyboard.keys) {
            val code = key.codes[0]
            if (code in 97..122) { // a-z
                key.label = if (shifted) {
                    (code - 32).toChar().toString() // Convert to uppercase
                } else {
                    code.toChar().toString() // Keep as lowercase
                }
            }
        }
    }

    override fun onPress(p0: Int) {}
    override fun onRelease(p0: Int) {}
    override fun onText(p0: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}