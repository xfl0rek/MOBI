package pl.mobi

import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.AlertDialog
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View


class MainActivity : AppCompatActivity() {

    private var budget: Double = 0.0
    private var expenses = mutableListOf<Pair<String, Double>>()
    private lateinit var expenseAdapter: ExpenseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val budgetTextView: TextView = findViewById(R.id.budgetTextView)
        val remainingTextView: TextView = findViewById(R.id.remainingTextView)
        val addBudgetButton: Button = findViewById(R.id.addBudgetButton)
        val addExpenseButton: Button = findViewById(R.id.addExpenseButton)
        val expenseRecyclerView: RecyclerView = findViewById(R.id.expenseRecyclerView)

        expenseAdapter = ExpenseAdapter(expenses)
        expenseRecyclerView.layoutManager = LinearLayoutManager(this)
        expenseRecyclerView.adapter = expenseAdapter

        addBudgetButton.setOnClickListener {
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                hint = "Enter your budget"
            }
            AlertDialog.Builder(this)
                .setTitle("Set Budget")
                .setView(input)
                .setPositiveButton("OK") { _, _ ->
                    budget = input.text.toString().toDoubleOrNull() ?: 0.0
                    budgetTextView.text = "Budget: $budget"
                    updateRemaining(remainingTextView)
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
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(inputName)
                addView(inputAmount)
            }
            AlertDialog.Builder(this)
                .setTitle("Add Expense")
                .setView(layout)
                .setPositiveButton("OK") { _, _ ->
                    val name = inputName.text.toString()
                    val amount = inputAmount.text.toString().toDoubleOrNull() ?: 0.0
                    if (name.isNotEmpty() && amount > 0) {
                        expenses.add(name to amount)
                        expenseAdapter.notifyDataSetChanged()
                        updateRemaining(remainingTextView)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateRemaining(remainingTextView: TextView) {
        val totalExpenses = expenses.sumOf { it.second }
        val remaining = budget - totalExpenses
        remainingTextView.text = "Remaining: $remaining"
    }
}

class ExpenseAdapter(private val expenses: List<Pair<String, Double>>) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val (name, amount) = expenses[position]
        holder.nameView.text = name
        holder.amountView.text = "$amount"
    }

    override fun getItemCount(): Int = expenses.size

    class ExpenseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameView: TextView = view.findViewById(android.R.id.text1)
        val amountView: TextView = view.findViewById(android.R.id.text2)
    }
}
