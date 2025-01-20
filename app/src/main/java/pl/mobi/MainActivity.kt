package pl.mobi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private var budget: Double = 0.0  // Current budget in selected currency
    private var budgetInPLN: Double = 0.0
    private var expenses = mutableListOf<Triple<String, String, Double>>()
    private lateinit var expenseAdapter: ExpenseAdapter
    private var selectedCurrency: Currency = Currency.getInstance("PLN")  // Default PLN
    private var categories = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val expensePieChart: PieChart = findViewById(R.id.expensePieChart)

        // wczytywanie zmiennych - zmienic pozniej na firebase
        val preferences = getSharedPreferences("Settings", MODE_PRIVATE)
        budget = preferences.getFloat("Budget", 0.0f).toDouble()
        budgetInPLN = preferences.getFloat("BudgetInPLN", 0.0f).toDouble()
        val currencyCode = preferences.getString("CurrencyCode", "PLN") ?: "PLN"
        selectedCurrency = Currency.getInstance(currencyCode)

        // Powiązanie widoków
        val budgetTextView: TextView = findViewById(R.id.budgetTextView)
        val remainingTextView: TextView = findViewById(R.id.remainingTextView)
        val addBudgetButton: Button = findViewById(R.id.addBudgetButton)
        val addExpenseButton: Button = findViewById(R.id.addExpenseButton)
        val settingsButton: Button = findViewById(R.id.settingsButton)
        val expenseRecyclerView: RecyclerView = findViewById(R.id.expenseRecyclerView)

        loadCategories()

        // Ustawienie RecyclerView
        expenseAdapter = ExpenseAdapter(expenses, selectedCurrency)
        expenseRecyclerView.layoutManager = LinearLayoutManager(this)
        expenseRecyclerView.adapter = expenseAdapter

        // Wyświetlenie budżetu
        updateBudgetTextView(budgetTextView)
        updateRemaining(remainingTextView)

        updatePieChart(expensePieChart)

        intent.getStringExtra("SELECTED_CURRENCY")?.let { newCurrencyCode ->
            intent.getDoubleExtra("EXCHANGE_RATE", 0.0).let {exchangeRate ->
                println(newCurrencyCode)
                println(exchangeRate)
                if (newCurrencyCode != selectedCurrency.currencyCode) {
                    if (newCurrencyCode == "PLN") {
                        budget = budgetInPLN
                    } else {
                        budget = budgetInPLN / exchangeRate
                        println(budget)
                        println(budgetInPLN)
                        println(exchangeRate)
                    }
                    selectedCurrency = Currency.getInstance(newCurrencyCode)
                    preferences.edit().putString("CurrencyCode", selectedCurrency.currencyCode).apply()
                    updateBudgetTextView(budgetTextView)
                    updateRemaining(remainingTextView)
                }
            }
        }

        // Obsługa przycisku dodawania budżetu
        addBudgetButton.setOnClickListener {
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                hint = "Enter your budget"
            }
            AlertDialog.Builder(this)
                .setTitle("Set Budget")
                .setView(input)
                .setPositiveButton("OK") { _, _ ->
                    val newBudget = input.text.toString().toDoubleOrNull()
                    if (newBudget != null) {
                        if (selectedCurrency.currencyCode == "PLN") {
                            budgetInPLN = newBudget
                            preferences.edit().putFloat("BudgetInPLN", budgetInPLN.toFloat()).apply()
                        }
                        budget = newBudget
                        preferences.edit().putFloat("Budget", budget.toFloat()).apply()
                        updateBudgetTextView(budgetTextView)
                        updateRemaining(remainingTextView)
                    } else {
                        Toast.makeText(this, "Invalid budget value", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Obsługa przycisku dodawania wydatku
        addExpenseButton.setOnClickListener {
            val inputName = EditText(this).apply {
                hint = "Expense name"
            }
            val inputAmount = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                hint = "Expense amount"
            }
            val categorySpinner = Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, categories)
            }
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(inputName)
                addView(inputAmount)
                addView(categorySpinner)
            }
            AlertDialog.Builder(this)
                .setTitle("Add Expense")
                .setView(layout)
                .setPositiveButton("OK") { _, _ ->
                    val name = inputName.text.toString()
                    val amount = inputAmount.text.toString().toDoubleOrNull() ?: 0.0
                    val category = categorySpinner.selectedItem?.toString() ?: "Uncategorized"
                    if (name.isNotEmpty() && amount > 0) {
                        expenses.add(Triple(name, category, amount))
                        expenseAdapter.notifyDataSetChanged()
                        updateRemaining(remainingTextView)
                        updatePieChart(expensePieChart)
                    } else {
                        Toast.makeText(this, "Invalid expense data", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Obsługa przycisku ustawień
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updatePieChart(pieChart: PieChart) {
        val categorySums = expenses.groupBy { it.second }.mapValues { entry ->
            entry.value.sumOf { it.third }
        }

        val entries = categorySums.map { (category, total) ->
            PieEntry(total.toFloat(), category)
        }

        val dataSet = PieDataSet(entries, "Expenses by Category").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 14f
        }

        pieChart.data = PieData(dataSet)
        pieChart.description.isEnabled = false
        pieChart.invalidate() // Odśwież wykres
    }


    private fun updateBudgetTextView(budgetTextView: TextView) {
        budgetTextView.text = "Budget: $budget ${selectedCurrency.symbol}"
    }

    private fun updateRemaining(remainingTextView: TextView) {
        val totalExpenses = expenses.sumOf { it.third }
        val remaining = budget - totalExpenses
        remainingTextView.text = "Remaining: $remaining ${selectedCurrency.symbol}"

        if (remaining < 0) {
            AlertDialog.Builder(this)
                .setTitle("Budget Exceeded")
                .setMessage("You have exceeded your budget by ${-remaining} ${selectedCurrency.symbol}.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun loadCategories() {
        val file = getFileStreamPath("categories.txt")
        if (!file.exists()) {
            categories = mutableListOf("Food", "Transport", "Entertainment", "Other")
            saveCategories()
        } else {
            categories = openFileInput("categories.txt").bufferedReader().readLines().toMutableList()
        }
    }

    private fun saveCategories() {
        openFileOutput("categories.txt", Context.MODE_PRIVATE).use { output ->
            categories.forEach { output.write("$it\n".toByteArray()) }
        }
    }
}

class ExpenseAdapter(
    private val expenses: List<Triple<String, String, Double>>,
    private val currency: Currency
) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val (name, category, amount) = expenses[position]
        holder.nameView.text = "$name ($category)"
        holder.amountView.text = "${currency.symbol}$amount"
    }

    override fun getItemCount(): Int = expenses.size

    class ExpenseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameView: TextView = view.findViewById(android.R.id.text1)
        val amountView: TextView = view.findViewById(android.R.id.text2)
    }
}
