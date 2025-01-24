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

    fun removeExpense(index: Int) {
        if (index in expenses.indices) {
            expenses.removeAt(index)
        }
    }

    fun getAllExpenses(): MutableList<Expense> {
        return expenses
    }

    fun updateExpense(expense: Expense) {
        val index = expenses.indexOfFirst { it.id == expense.id }
        if (index != -1) {
            expenses[index] = expense
        }
    }

    fun getExpensesByCategory(category: String): List<Expense> {
        return expenses.filter { it.category == category }
    }

    fun clearExpenses() {
        expenses.clear()
    }
}
