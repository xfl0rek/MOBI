package pl.mobi

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private val TAG = "SettingsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val currencySpinner: Spinner = findViewById(R.id.currencySpinner)
        val saveButton: Button = findViewById(R.id.saveSettingsButton)
        val availableCurrencies = listOf("PLN", "USD", "EUR")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, availableCurrencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        currencySpinner.adapter = adapter

        saveButton.setOnClickListener {
            val selectedCurrencyCode = currencySpinner.selectedItem.toString()
            Log.d(TAG, "Selected currency: $selectedCurrencyCode")

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    if (selectedCurrencyCode != "PLN") {
                        val exchangeRate = CurrencyConverter.fetchExchangeRate(selectedCurrencyCode)
                        if (exchangeRate != null) {
                            Log.d(TAG, "Exchange rate fetched: ${exchangeRate.rate}")
                            // Create intent to return to MainActivity
                            val intent = Intent(this@SettingsActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                putExtra("SELECTED_CURRENCY", selectedCurrencyCode)
                                putExtra("EXCHANGE_RATE", exchangeRate.rate)
                            }

                            Log.d(TAG, "Starting MainActivity with currency: $selectedCurrencyCode and exchangeRate ${exchangeRate.rate}")
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        val intent = Intent(this@SettingsActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("SELECTED_CURRENCY", selectedCurrencyCode)
                        }

                        Log.d(TAG, "Starting MainActivity with currency: $selectedCurrencyCode")
                        startActivity(intent)
                        finish()
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error during currency conversion: ${e.message}")
                    Toast.makeText(
                        this@SettingsActivity,
                        "Error during currency conversion: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}