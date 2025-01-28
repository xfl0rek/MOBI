package pl.mobi

import android.annotation.SuppressLint
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
    private var categories = mutableListOf("Food", "Transport", "Entertainment", "Other")
    private lateinit var auth: FirebaseAuth

    @SuppressLint("MissingInflatedId")
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
        val resetBudgetButton: Button = findViewById(R.id.resetBudgetButton)

        resetBudgetButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Budget")
                .setMessage("Are you sure you want to reset your budget and all expenses?")
                .setPositiveButton("Yes") { _, _ ->
                    resetBudget(budgetTextView, remainingTextView, expensePieChart)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

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
                updateBudgetTextView(budgetTextView)
            }
        }

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
                }
            }
        }

        expenseAdapter = currency?.let { ExpenseAdapter(ExpensesStore.getAllExpenses(), it) }!!
        expenseRecyclerView.layoutManager = LinearLayoutManager(this)
        expenseRecyclerView.adapter = expenseAdapter

        loadExpensesFromFirestore(remainingTextView, expensePieChart)

        updateBudgetTextView(budgetTextView)

        updatePieChart(expensePieChart)

        addBudgetButton.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_set_budget, null)
            val input = dialogView.findViewById<EditText>(R.id.budgetInput)

            val dialog = AlertDialog.Builder(this)
                .setTitle("Set Budget")
                .setView(dialogView)
                .setPositiveButton("OK") { _, _ ->
                    val newBudget = input.text.toString().toDoubleOrNull()
                    if (newBudget != null) {
                        if (currency!!.currencyCode == "PLN") {
                            budgetInPLN = newBudget
                            auth.currentUser?.uid?.let { uid ->
                                saveVariableToFirestore("mobi", "budgetsInPLN", uid, budgetInPLN!!)
                            }
                        }
                        budget = newBudget
                        auth.currentUser?.uid?.let { uid ->
                            saveVariableToFirestore("mobi", "budgets", uid, budget!!)
                            saveVariableToFirestore("mobi", "currencyCode", uid, currency!!.currencyCode)
                        }
                        updateBudgetTextView(budgetTextView)
                        updateRemaining(remainingTextView)
                    } else {
                        Toast.makeText(this, "Invalid budget value", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .create()

            dialog.show()
        }

        addExpenseButton.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_add_expense, null)
            val inputName = dialogView.findViewById<EditText>(R.id.expenseNameInput)
            val inputAmount = dialogView.findViewById<EditText>(R.id.expenseAmountInput)
            val categorySpinner = dialogView.findViewById<Spinner>(R.id.expenseCategorySpinner)

            categorySpinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                categories
            )

            val dialog = AlertDialog.Builder(this)
                .setTitle("Add Expense")
                .setView(dialogView)
                .setPositiveButton("OK") { _, _ ->
                    val name = inputName.text.toString()
                    val amount = inputAmount.text.toString().toDoubleOrNull() ?: 0.0
                    val category = categorySpinner.selectedItem?.toString() ?: "Uncategorized"
                    if (name.isNotEmpty() && amount > 0) {
                        if (currency!!.currencyCode == "PLN") {
                            saveExpenseToFirestore(name, category, amount, amount) { expenseId ->
                                if (expenseId != null) {
                                    val expense = Expense(
                                        expenseId, name, category, amount, amount, currency!!.currencyCode
                                    )
                                    ExpensesStore.addExpense(expense)

                                    updatePieChart(expensePieChart)
                                    updateRemaining(remainingTextView)
                                    expenseAdapter.notifyDataSetChanged()
                                }
                            }
                        } else {
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    val exchangeRate = CurrencyConverter.fetchExchangeRate(currency!!.currencyCode)
                                    val amountInPLN = amount * (exchangeRate?.rate ?: 1.0)
                                    saveExpenseToFirestore(name, category, amount, amountInPLN) { expenseId ->
                                        if (expenseId != null) {
                                            val expense = Expense(
                                                expenseId, name, category, amount, amountInPLN, currency!!.currencyCode
                                            )
                                            ExpensesStore.addExpense(expense)

                                            updatePieChart(expensePieChart)
                                            updateRemaining(remainingTextView)
                                            expenseAdapter.notifyDataSetChanged()
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("Error fetching exchange rate: $e")
                                }
                            }
                        }
                        updatePieChart(expensePieChart)
                        updateRemaining(remainingTextView)
                    } else {
                        Toast.makeText(this, "Invalid expense data", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .create()

            dialog.show()
        }



        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updatePieChart(pieChart: PieChart) {
        expenses = ExpensesStore.getAllExpenses().map { expense ->
            Triple(expense.name, expense.category, expense.amount)
        }.toMutableList()

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
        pieChart.invalidate()
    }

    private fun updateBudgetTextView(budgetTextView: TextView) {
        val formattedBudget = String.format("%.2f", budget)
        budgetTextView.text = "Budget: $formattedBudget ${currency?.symbol}"
    }

    private fun updateRemaining(remainingTextView: TextView) {
        val totalExpenses = ExpensesStore.getAllExpenses().sumOf { expense ->
            if (expense.currency == "PLN") expense.amountInPLN else expense.amount
        }

        val remaining = budget?.minus(totalExpenses)
        val formattedRemaining = String.format("%.2f", remaining)
        remainingTextView.text = "Remaining: ${formattedRemaining ?: 0.0} ${currency?.symbol}"

        if (remaining != null && remaining < 0) {
            AlertDialog.Builder(this)
                .setTitle("Budget Exceeded")
                .setMessage("You have exceeded your budget by ${"%.2f".format(-remaining)} ${currency?.symbol}.")
                .setPositiveButton("OK", null)
                .show()
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

    private fun resetBudget(budgetTextView: TextView, remainingTextView: TextView, expensePieChart: PieChart) {
        budget = 0.0
        budgetInPLN = 0.0

        auth.currentUser?.uid?.let { userId ->
            saveVariableToFirestore("mobi", "budgets", userId, budget!!)
            saveVariableToFirestore("mobi", "budgetsInPLN", userId, budgetInPLN!!)
        }

        ExpensesStore.clearExpenses()
        expenseAdapter.notifyDataSetChanged()

        ExpensesStore.clearExpenses()
        auth.currentUser?.uid?.let { userId ->
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("mobi").document("expenses").collection(userId).get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        document.reference.delete()
                    }
                    updateRemaining(remainingTextView)
                    updatePieChart(expensePieChart)
                }
                .addOnFailureListener { e ->
                    println("Error resetting expenses: $e")
                }
        }

        updateBudgetTextView(budgetTextView)
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
        val (id, name, category, amount, amountInPLN, currency) = expenses[position]
        val cur = Currency.getInstance(currency)
        val formattedAmount = String.format("%.2f", amount)
        holder.nameView.text = "$name ($category)"
        holder.amountView.text = "${cur.symbol}$formattedAmount"
    }

    override fun getItemCount(): Int = expenses.size

    class ExpenseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameView: TextView = view.findViewById(android.R.id.text1)
        val amountView: TextView = view.findViewById(android.R.id.text2)
    }
}
