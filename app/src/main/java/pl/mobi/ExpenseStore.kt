package pl.mobi

data class Expense(
    val id: String,
    val name: String,
    val category: String,
    val amount: Double,
    val amountInPLN: Double,
    val currency: String
)

object ExpensesStore {
    private val expenses: MutableList<Expense> = mutableListOf()

    fun addExpense(expense: Expense) {
        expenses.add(expense)
    }

    fun getAllExpenses(): MutableList<Expense> {
        return expenses
    }

    fun clearExpenses() {
        expenses.clear()
    }
}
