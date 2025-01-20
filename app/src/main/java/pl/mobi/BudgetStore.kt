package pl.mobi

import java.util.Currency

object BudgetStore {
    var budgetInPLN: Double? = 0.0
    var budget: Double? = 0.0
    var currency: Currency? = Currency.getInstance("PLN")
}