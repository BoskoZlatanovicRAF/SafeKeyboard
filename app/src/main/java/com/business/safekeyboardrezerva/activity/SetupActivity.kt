package com.business.safekeyboardrezerva.activity

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.business.safekeyboardrezerva.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class SetupActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var ageInput: EditText
    private lateinit var enableButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        emailInput = findViewById(R.id.email_input)
        ageInput = findViewById(R.id.child_age_input)
        enableButton = findViewById(R.id.enable_keyboard_button)

        enableButton.isEnabled = false

        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val email = emailInput.text.toString().trim()
                val age = ageInput.text.toString().trim()
                enableButton.isEnabled = email.isNotEmpty() && age.isNotEmpty()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        emailInput.addTextChangedListener(textWatcher)
        ageInput.addTextChangedListener(textWatcher)

        enableButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val age = ageInput.text.toString().trim()

            sendToApi(email, age)
        }
    }

    private fun sendToApi(email: String, age: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://your-api-url.com/register") // Replace with your endpoint
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val body = """{
                    "email": "$email",
                    "childAge": "$age"
                }"""

                conn.outputStream.use { os ->
                    os.write(body.toByteArray())
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    runOnUiThread {
                        Toast.makeText(this@SetupActivity, "Success!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        startActivity(intent)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@SetupActivity, "Error: $responseCode", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@SetupActivity, "API error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
