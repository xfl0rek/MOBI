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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {
    private var budget: Double = 0.0
    private var budgetInPLN: Double = 0.0
    private var expenses = mutableListOf<Triple<String, String, Double>>()
    private lateinit var expenseAdapter: ExpenseAdapter
    private var selectedCurrency: Currency = Currency.getInstance("PLN")
    private var categories = mutableListOf<String>()
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val expensePieChart: PieChart = findViewById(R.id.expensePieChart)

        auth = FirebaseAuth.getInstance()

        val budgetTextView: TextView = findViewById(R.id.budgetTextView)
        val remainingTextView: TextView = findViewById(R.id.remainingTextView)
        val addBudgetButton: Button = findViewById(R.id.addBudgetButton)
        val addExpenseButton: Button = findViewById(R.id.addExpenseButton)
        val settingsButton: Button = findViewById(R.id.settingsButton)
        val expenseRecyclerView: RecyclerView = findViewById(R.id.expenseRecyclerView)

        val preferences = getSharedPreferences("Settings", MODE_PRIVATE)
        budget = preferences.getFloat("Budget", 0.0f).toDouble()
        budgetInPLN = preferences.getFloat("BudgetInPLN", 0.0f).toDouble()
        val currencyCode = preferences.getString("CurrencyCode", "PLN") ?: "PLN"
        selectedCurrency = Currency.getInstance(currencyCode)

        auth.currentUser?.uid?.let { userId ->
            CoroutineScope(Dispatchers.IO).launch {
                val budgetDeferred = readVariableFromFirestore("mobi", "budgets", userId)
                val budgetInPLNDeferred = readVariableFromFirestore("mobi", "budgetsInPLN", userId)
                val currencyDeferred = readVariableFromFirestore("mobi", "currencyCode", userId)

                val budget = budgetDeferred.await() as? Double ?: 0.0
                val budgetInPLN = budgetInPLNDeferred.await() as? Double ?: 0.0
                val selectedCurrencyCode = currencyDeferred.await() as? String ?: "PLN"
                val selectedCurrency = Currency.getInstance(selectedCurrencyCode)

                withContext(Dispatchers.Main) {
                    this@MainActivity.budget = budget
                    this@MainActivity.budgetInPLN = budgetInPLN
                    this@MainActivity.selectedCurrency = selectedCurrency

                    updateBudgetTextView(budgetTextView)
                    updateRemaining(remainingTextView)
                    updatePieChart(expensePieChart)
                }
            }
        }

        loadCategories()
        loadExpensesFromFirestore(remainingTextView, expensePieChart)

        expenseAdapter = ExpenseAdapter(expenses, selectedCurrency)
        expenseRecyclerView.layoutManager = LinearLayoutManager(this)
        expenseRecyclerView.adapter = expenseAdapter

        updateBudgetTextView(budgetTextView)
        updateRemaining(remainingTextView)

        updatePieChart(expensePieChart)

        intent.getStringExtra("SELECTED_CURRENCY")?.let { newCurrencyCode ->
            intent.getDoubleExtra("EXCHANGE_RATE", 0.0).let {exchangeRate ->
                if (newCurrencyCode != selectedCurrency.currencyCode) {
                    if (newCurrencyCode == "PLN") {
                        budget = budgetInPLN
                        auth.currentUser?.uid?.let {
                            saveVariableToFirestore("mobi", "budgets",
                                it, budget)
                        }
                    } else {
                        budget = budgetInPLN / exchangeRate
                        auth.currentUser?.uid?.let {
                            saveVariableToFirestore("mobi", "budgets",
                                it, budget)
                        }
                    }
                    selectedCurrency = Currency.getInstance(newCurrencyCode)
                    auth.currentUser?.uid?.let {
                        saveVariableToFirestore("mobi", "currencyCode",
                            it, selectedCurrency.currencyCode)
                    }
                    preferences.edit().putString("CurrencyCode", selectedCurrency.currencyCode).apply()
                    updateBudgetTextView(budgetTextView)
                    updateRemaining(remainingTextView)
                }
            }
        }

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
                            auth.currentUser?.uid?.let { it1 ->
                                saveVariableToFirestore("mobi", "budgetsInPLN",
                                    it1, budgetInPLN)
                            }
                            preferences.edit().putFloat("BudgetInPLN", budgetInPLN.toFloat()).apply()
                        }
                        budget = newBudget
                        preferences.edit().putFloat("Budget", budget.toFloat()).apply()
                        auth.currentUser?.uid?.let { it1 ->
                            saveVariableToFirestore("mobi", "budgets",
                                it1, budget)
                        }
                        auth.currentUser?.uid?.let { it1 ->
                            saveVariableToFirestore("mobi", "currencyCode",
                                it1, selectedCurrency.currencyCode)
                        }
                        updateBudgetTextView(budgetTextView)
                        updateRemaining(remainingTextView)
                    } else {
                        Toast.makeText(this, "Invalid budget value", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

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
                        saveExpenseToFirestore(name, category, amount)
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

    private fun saveVariableToFirestore(collection: String, document: String, key: String, value: Any) {
        val firestore = FirebaseFirestore.getInstance()
        val data = hashMapOf(key to value)
        firestore.collection(collection).document(document).set(data)
            .addOnSuccessListener {
                println("Zmienna zapisana")
            }
            .addOnFailureListener {
                println("Błąd zapisu")
            }
    }

    private fun readVariableFromFirestore(collection: String, document: String, key: String): Deferred<Any?> {
        val firestore = FirebaseFirestore.getInstance()
        return CoroutineScope(Dispatchers.IO).async {
            try {
                val documentSnapshot = firestore.collection(collection).document(document).get().await()
                if (documentSnapshot.exists()) {
                    documentSnapshot.get(key)
                } else {
                    null
                }
            } catch (exception: Exception) {
                null
            }
        }
    }

    private fun saveExpenseToFirestore(name: String, category: String, amount: Double) {
        val userId = auth.currentUser?.uid ?: return
        val expenseData = hashMapOf(
            "name" to name,
            "category" to category,
            "amount" to amount,
            "currency" to selectedCurrency.currencyCode
        )
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("mobi").document("expenses").collection(userId).add(expenseData)
            .addOnSuccessListener {
                println("Expense saved")
            }
            .addOnFailureListener { e ->
                println("Error saving expense: $e")
            }
    }

    private fun loadExpensesFromFirestore(remainingTextView: TextView, expensePieChart: PieChart) {
        val userId = auth.currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("mobi").document("expenses").collection(userId).get()
            .addOnSuccessListener { documents ->
                expenses.clear()
                for (document in documents) {
                    val name = document.getString("name") ?: ""
                    val category = document.getString("category") ?: "Uncategorized"
                    val amount = document.getDouble("amount") ?: 0.0
                    expenses.add(Triple(name, category, amount))
                }
                expenseAdapter.notifyDataSetChanged()
                updateRemaining(remainingTextView)
                updatePieChart(expensePieChart)
            }
            .addOnFailureListener { e ->
                println("Error loading expenses: $e")
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
