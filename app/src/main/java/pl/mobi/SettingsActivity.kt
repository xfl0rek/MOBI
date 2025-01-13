package pl.mobi

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    // Mapa stałych kursów walut
    private val exchangeRates = mapOf(
        "PLN" to 1.0,
        "USD" to 0.25,
        "EUR" to 0.22
    )

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
            val preferences = getSharedPreferences("Settings", MODE_PRIVATE)
            val previousCurrencyCode = preferences.getString("CurrencyCode", "PLN") ?: "PLN"

            if (selectedCurrencyCode != previousCurrencyCode) {
                val conversionRate = getConversionRate(previousCurrencyCode, selectedCurrencyCode)

                val budget = preferences.getFloat("Budget", 0.0f).toDouble()
                val newBudget = budget * conversionRate

                val editor = preferences.edit()
                editor.putFloat("Budget", newBudget.toFloat())
                editor.putString("CurrencyCode", selectedCurrencyCode)
                editor.apply()

                Toast.makeText(this@SettingsActivity, "Przewalutowano budżet na $selectedCurrencyCode", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@SettingsActivity, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    private fun getConversionRate(fromCurrency: String, toCurrency: String): Double {
        val fromRate = exchangeRates[fromCurrency] ?: 1.0
        val toRate = exchangeRates[toCurrency] ?: 1.0
        return toRate / fromRate
    }
}
