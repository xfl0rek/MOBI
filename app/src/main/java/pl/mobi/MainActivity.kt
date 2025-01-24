package pl.mobi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
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
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import pl.mobi.BudgetStore.budget
import pl.mobi.BudgetStore.budgetInPLN
import pl.mobi.BudgetStore.currency
import pl.mobi.ExchangeRateStore.exchangeRate
import pl.mobi.ExchangeRateStore.selectedCurrency


class MainActivity : AppCompatActivity() {
    private var expenses = mutableListOf<Triple<String, String, Double>>()
    private lateinit var expenseAdapter: ExpenseAdapter
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

        if (exchangeRate != null) {
            if (selectedCurrency != currency?.currencyCode) {
                if (selectedCurrency == "PLN") {
                    budget = budgetInPLN
                    ExpensesStore.getAllExpenses().replaceAll { expense ->
                        updateExpenseInFirestore(expense.id, expense.name, expense.category,
                            expense.amountInPLN, expense.amountInPLN, selectedCurrency!!
                        )
                        expense.copy(amount = expense.amountInPLN)
                    }
                    auth.currentUser?.uid?.let {
                            budget?.let { it1 ->
                                saveVariableToFirestore("mobi", "budgets",
                                    it, it1
                                )
                            }
                        }
                } else {
                    budget = budgetInPLN?.div(exchangeRate!!)
                    ExpensesStore.getAllExpenses().replaceAll { expense ->
                        updateExpenseInFirestore(expense.id, expense.name, expense.category,
                            expense.amountInPLN / exchangeRate!!, expense.amountInPLN,
                            selectedCurrency!!)
                        expense.copy(amount = expense.amountInPLN / exchangeRate!!)
                    }
                    auth.currentUser?.uid?.let {
                            saveVariableToFirestore("mobi", "budgets",
                                it, budget!!
                            )
                        }
                }
                currency = Currency.getInstance(selectedCurrency)
                auth.currentUser?.uid?.let {
                    selectedCurrency?.let { it1 ->
                        saveVariableToFirestore("mobi", "currencyCode",
                            it, it1
                        )
                    }
                    }
                updateRemaining(remainingTextView)
                updateBudgetTextView(budgetTextView)
//                expenseAdapter.notifyDataSetChanged()
            }
        }

        println(ExpensesStore.getAllExpenses())

        auth.currentUser?.uid?.let { userId ->
            CoroutineScope(Dispatchers.IO).launch {
                val budgetDeferred = readVariableFromFirestore("mobi", "budgets", userId)
                val budgetInPLNDeferred = readVariableFromFirestore("mobi", "budgetsInPLN", userId)
                val currencyDeferred = readVariableFromFirestore("mobi", "currencyCode", userId)

                budget = budgetDeferred.await() as? Double ?: 0.0
                budgetInPLN = budgetInPLNDeferred.await() as? Double ?: 0.0
                val selectedCurrencyCode = currencyDeferred.await() as? String ?: "PLN"
                currency = Currency.getInstance(selectedCurrencyCode)

                withContext(Dispatchers.Main) {
                    updateBudgetTextView(budgetTextView)
                    updateRemaining(remainingTextView)
                    updatePieChart(expensePieChart)
//                    expenseAdapter.notifyDataSetChanged()
                }
            }
        }

        expenseAdapter = currency?.let { ExpenseAdapter(ExpensesStore.getAllExpenses(), it) }!!
        expenseRecyclerView.layoutManager = LinearLayoutManager(this)
        expenseRecyclerView.adapter = expenseAdapter

        loadCategories()
        loadExpensesFromFirestore(remainingTextView, expensePieChart)

        updateBudgetTextView(budgetTextView)
        updateRemaining(remainingTextView)

        updatePieChart(expensePieChart)

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
                        if (currency!!.currencyCode == "PLN") {
                            budgetInPLN = newBudget
                            auth.currentUser?.uid?.let { it1 ->
                                saveVariableToFirestore("mobi", "budgetsInPLN",
                                    it1, budgetInPLN!!
                                )
                            }
                        }
                        budget = newBudget
                        auth.currentUser?.uid?.let { it1 ->
                            saveVariableToFirestore("mobi", "budgets",
                                it1, budget!!
                            )
                        }
                        auth.currentUser?.uid?.let { it1 ->
                            saveVariableToFirestore("mobi", "currencyCode",
                                it1, currency!!.currencyCode)
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
                        if (currency!!.currencyCode == "PLN") {
                            saveExpenseToFirestore(name, category, amount, amount) {expenseId ->
                                println(expenseId + "EXPENSE ID")
                                if (expenseId != null) {
                                    val expense = Expense(expenseId, name, category, amount, amount, currency!!.currencyCode)
                                    ExpensesStore.addExpense(expense)

                                    expenseAdapter.notifyDataSetChanged()
                                    updateRemaining(remainingTextView)
                                    updatePieChart(expensePieChart)
                                }
                            }
                        } else {
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    val exchangeRate = CurrencyConverter.fetchExchangeRate(currency!!.currencyCode)
                                    val amountInPLN = amount * exchangeRate?.rate!!
                                    saveExpenseToFirestore(name, category, amount, amountInPLN) {expenseId ->
                                        if (expenseId != null) {
                                            val expense = Expense(expenseId, name, category, amount,
                                                amountInPLN, currency!!.currencyCode)
                                            ExpensesStore.addExpense(expense)

                                            expenseAdapter.notifyDataSetChanged()
                                            updateRemaining(remainingTextView)
                                            updatePieChart(expensePieChart)
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("Something went wrong during fetching exchange rate in expense")
                                }
                            }

                        }

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
        budgetTextView.text = "Budget: $budget ${currency?.symbol}"
    }

    private fun updateRemaining(remainingTextView: TextView) {
        val totalExpenses = expenses.sumOf { it.third }
        val remaining = budget?.minus(totalExpenses)
        remainingTextView.text = "Remaining: $remaining ${currency?.symbol}"

        if (remaining != null) {
            if (remaining < 0) {
                AlertDialog.Builder(this)
                    .setTitle("Budget Exceeded")
                    .setMessage("You have exceeded your budget by ${-remaining} ${currency?.symbol}.")
                    .setPositiveButton("OK", null)
                    .show()
            }
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

    private fun saveExpenseToFirestore(
        name: String,
        category: String,
        amount: Double,
        amountInPLN: Double,
        callback: (String?) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: return
        val expenseData = hashMapOf(
            "name" to name,
            "category" to category,
            "amount" to amount,
            "amountInPLN" to amountInPLN,
            "currency" to (currency?.currencyCode ?: "PLN")
        )
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("mobi").document("expenses").collection(userId).add(expenseData)
            .addOnSuccessListener { documentReference ->
                callback(documentReference.id)
            }
            .addOnFailureListener { e ->
                println("Error saving expense: $e")
                callback(null)
            }
    }


    private fun loadExpensesFromFirestore(remainingTextView: TextView, expensePieChart: PieChart) {
        val userId = auth.currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("mobi").document("expenses").collection(userId).get()
            .addOnSuccessListener { documents ->
                ExpensesStore.clearExpenses()
                for (document in documents) {
                    val id = document.id
                    val name = document.getString("name") ?: ""
                    val category = document.getString("category") ?: "Uncategorized"
                    val amount = document.getDouble("amount") ?: 0.0
                    val amountInPLN = document.getDouble("amountInPLN") ?: 0.0
                    val currencyCode = document.getString("currency") ?: "PLN"

                    val expense = Expense(id, name, category, amount, amountInPLN, currencyCode)
                    ExpensesStore.addExpense(expense)
                }
                expenseAdapter.notifyDataSetChanged()
                updateRemaining(remainingTextView)
                updatePieChart(expensePieChart)
            }
            .addOnFailureListener { e ->
                println("Error loading expenses: $e")
            }
    }

    private fun updateExpenseInFirestore(
        expenseId: String,
        name: String,
        category: String,
        amount: Double,
        amountInPLN: Double,
        currency: String
    ) {
        val userId = auth.currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()
        val expenseData = mapOf(
            "name" to name,
            "category" to category,
            "amount" to amount,
            "amountInPLN" to amountInPLN,
            "currency" to currency
        )

        firestore.collection("mobi").document("expenses").collection(userId).document(expenseId)
            .set(expenseData, SetOptions.merge())
            .addOnSuccessListener {
                println("Expense with ID $expenseId successfully updated.")
            }
            .addOnFailureListener { e ->
                println("Error updating expense: $e")
            }
    }

}

class ExpenseAdapter(
    private val expenses: MutableList<Expense>,
    private val currency: Currency
) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val (id, name, category, amount) = expenses[position]
        holder.nameView.text = "$name ($category)"
        holder.amountView.text = "${currency.symbol}$amount"
    }

    override fun getItemCount(): Int = expenses.size

    class ExpenseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameView: TextView = view.findViewById(android.R.id.text1)
        val amountView: TextView = view.findViewById(android.R.id.text2)
    }
}
