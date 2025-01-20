package pl.mobi

data class Expense(
    val name: String,
    val category: String,
    val amount: Double,
    val currency: String
)

object ExpensesStore {
    private val expenses: MutableList<Expense> = mutableListOf()

    // Add an expense to the store
    fun addExpense(expense: Expense) {
        expenses.add(expense)
    }

    // Remove an expense from the store by index
    fun removeExpense(index: Int) {
        if (index in expenses.indices) {
            expenses.removeAt(index)
        }
    }

    // Get all expenses
    fun getAllExpenses(): List<Expense> {
        return expenses
    }

    // Get expenses filtered by category
    fun getExpensesByCategory(category: String): List<Expense> {
        return expenses.filter { it.category == category }
    }

    // Clear all expenses
    fun clearExpenses() {
        expenses.clear()
    }
}
