package pl.mobi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ExchangeRate(
    val currency: String,
    val code: String,
    val rate: Double
)

class CurrencyConverter {
    companion object {
        private const val BASE_URL = "https://api.nbp.pl/api/exchangerates/rates/a"

        suspend fun fetchExchangeRate(currencyCode: String): ExchangeRate? {
            return withContext(Dispatchers.IO) {
                try {
                    val url = URL("$BASE_URL/${currencyCode.lowercase()}/today/?format=json")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"

                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }

                    val jsonResponse = JSONObject(response.toString())
                    val rates = jsonResponse.getJSONArray("rates")
                    val rate = rates.getJSONObject(0)

                    ExchangeRate(
                        currency = jsonResponse.getString("currency"),
                        code = jsonResponse.getString("code"),
                        rate = rate.getDouble("mid")
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }

        fun convertFromPLN(amountPLN: Double, exchangeRate: Double): Double {
            return amountPLN / exchangeRate
        }

        fun convertToPLN(amount: Double, exchangeRate: Double): Double {
            return amount * exchangeRate
        }
    }
}