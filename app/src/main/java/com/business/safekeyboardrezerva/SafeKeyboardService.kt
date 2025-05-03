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

    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        val keyboard = Keyboard(this, R.xml.qwerty)
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.isPreviewEnabled = false
        return keyboardView
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection = currentInputConnection ?: return


        when (primaryCode) {


            Keyboard.KEYCODE_DONE -> {
                val text = getTextFromInput()
                sendToApi(text)
            }
            Keyboard.KEYCODE_DELETE -> {
                inputConnection.deleteSurroundingText(1, 0)
            }
            else -> {
                val code = primaryCode.toChar()
                if(primaryCode == 20){

                }
                inputConnection.commitText(code.toString(), 1)
            }
        }
    }

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

    override fun onPress(p0: Int) {}
    override fun onRelease(p0: Int) {}
    override fun onText(p0: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}