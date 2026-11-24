package ru.productaccounting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.productaccounting.data.ExpenseDocument
import ru.productaccounting.data.ExpenseItem
import ru.productaccounting.data.MenuIngredient
import ru.productaccounting.data.MenuPosition
import ru.productaccounting.data.PaymentMethod
import ru.productaccounting.data.Product
import ru.productaccounting.data.ReceiptDocument
import ru.productaccounting.data.ReceiptItem
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as ProductAccountingApplication).database.appDao()
    private val sharing = SharingStarted.WhileSubscribed(5_000)

    val products = dao.observeProducts().stateIn(viewModelScope, sharing, emptyList())
    val paymentMethods = dao.observePaymentMethods().stateIn(viewModelScope, sharing, emptyList())
    val menuPositions = dao.observeMenuPositions().stateIn(viewModelScope, sharing, emptyList())
    val receiptDocuments = dao.observeReceiptDocuments().stateIn(viewModelScope, sharing, emptyList())
    val expenseDocuments = dao.observeExpenseDocuments().stateIn(viewModelScope, sharing, emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    fun receiptItems(documentId: Long) = dao.observeReceiptItems(documentId)
    fun expenseItems(documentId: Long) = dao.observeExpenseItems(documentId)
    fun ingredients(menuId: Long) = dao.observeIngredients(menuId)

    fun addProduct(name: String, unit: String) = launchAction("Товар добавлен") {
        require(name.isNotBlank()) { "Укажите название товара" }
        require(unit.isNotBlank()) { "Укажите единицу измерения" }
        dao.insertProduct(Product(name = name.trim(), unit = unit.trim()))
    }

    fun updateProduct(id: Long, name: String, unit: String) = launchAction("Товар обновлён") {
        require(name.isNotBlank()) { "Укажите название товара" }
        require(unit.isNotBlank()) { "Укажите единицу измерения" }
        dao.updateProduct(id, name.trim(), unit.trim())
    }

    fun addPaymentMethod(name: String) = launchAction("Способ оплаты добавлен") {
        require(name.isNotBlank()) { "Укажите название способа оплаты" }
        dao.insertPaymentMethod(PaymentMethod(name = name.trim()))
    }

    fun addMenuPosition(name: String) = launchAction("Позиция меню добавлена") {
        require(name.isNotBlank()) { "Укажите название позиции меню" }
        dao.insertMenuPosition(MenuPosition(name = name.trim()))
    }

    fun setIngredient(menuId: Long, productId: Long, quantity: String) = launchAction("Состав сохранён") {
        val normalized = positiveDecimal(quantity)
        dao.upsertIngredient(MenuIngredient(menuId, productId, normalized))
    }

    fun createReceiptDocument() = launchAction("Вкладка прихода создана") {
        dao.insertReceiptDocument(ReceiptDocument(date = LocalDate.now().toString()))
    }

    fun addReceiptItem(
        documentId: Long,
        productId: Long,
        quantity: String,
        price: String,
        totalCost: String,
    ) =
        launchAction("Приход добавлен") {
            val normalizedQuantity = positiveDecimal(quantity)
            require(price.isNotBlank() || totalCost.isNotBlank()) {
                "Укажите цену за единицу или общую стоимость"
            }
            val quantityDecimal = normalizedQuantity.toBigDecimal()
            val priceMinor = if (price.isNotBlank()) {
                parseMoney(price, "цену")
            } else {
                divideMoney(parseMoney(totalCost, "общую стоимость"), quantityDecimal)
            }
            val totalCostMinor = if (totalCost.isNotBlank()) {
                parseMoney(totalCost, "общую стоимость")
            } else {
                multiplyMoney(priceMinor, quantityDecimal)
            }
            dao.insertReceiptItem(ReceiptItem(
                documentId = documentId,
                productId = productId,
                quantity = normalizedQuantity,
                unitPriceMinor = priceMinor,
                totalCostMinor = totalCostMinor,
            ))
        }

    fun createExpenseDocument() = launchAction("Вкладка расхода создана") {
        dao.insertExpenseDocument(ExpenseDocument(date = LocalDate.now().toString()))
    }

    fun addExpenseItem(documentId: Long, menuId: Long, quantity: String, paymentMethodId: Long) =
        launchAction("Расход проведён по FIFO") {
            dao.addExpenseWithFifo(ExpenseItem(
                documentId = documentId,
                menuPositionId = menuId,
                quantity = positiveDecimal(quantity),
                paymentMethodId = paymentMethodId,
            ))
        }

    private fun launchAction(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _messages.emit(success) }
                .onFailure { _messages.emit(it.message ?: "Не удалось сохранить данные") }
        }
    }

    private fun positiveDecimal(value: String): String {
        val parsed = value.trim().replace(',', '.').toBigDecimalOrNull()
            ?: error("Введите корректное количество")
        require(parsed > BigDecimal.ZERO) { "Количество должно быть больше нуля" }
        return parsed.stripTrailingZeros().toPlainString()
    }

    private fun parseMoney(value: String, fieldName: String): Long {
        val parsed = value.trim().replace(',', '.').toBigDecimalOrNull()
            ?: error("Введите корректную $fieldName")
        require(parsed >= BigDecimal.ZERO) { "Стоимость не может быть отрицательной" }
        return parsed.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }

    private fun divideMoney(totalMinor: Long, quantity: BigDecimal): Long =
        BigDecimal.valueOf(totalMinor).divide(quantity, 0, RoundingMode.HALF_UP).longValueExact()

    private fun multiplyMoney(unitPriceMinor: Long, quantity: BigDecimal): Long =
        BigDecimal.valueOf(unitPriceMinor).multiply(quantity).setScale(0, RoundingMode.HALF_UP).longValueExact()
}
