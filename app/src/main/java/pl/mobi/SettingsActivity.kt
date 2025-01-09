package pl.mobi

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class SettingsActivity : AppCompatActivity() {

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
            val selectedCurrency = Currency.getInstance(selectedCurrencyCode)
            val preferences = getSharedPreferences("Settings", MODE_PRIVATE)
            val editor = preferences.edit()
            editor.putString("CurrencyCode", selectedCurrency.currencyCode)
            editor.apply()

            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
