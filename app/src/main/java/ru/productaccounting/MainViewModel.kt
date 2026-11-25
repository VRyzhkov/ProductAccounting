package ru.productaccounting

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.productaccounting.data.DataTransfer
import ru.productaccounting.data.ExpenseDocument
import ru.productaccounting.data.ExpenseItem
import ru.productaccounting.data.MenuIngredient
import ru.productaccounting.data.MenuPosition
import ru.productaccounting.data.PaymentMethod
import ru.productaccounting.data.Product
import ru.productaccounting.data.ReceiptDocument
import ru.productaccounting.data.ReceiptItem
import ru.productaccounting.data.TransferFile
import ru.productaccounting.data.TransferSection
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = (application as ProductAccountingApplication).database
    private val dao = database.appDao()
    private val transfer = DataTransfer(database)
    private val sharing = SharingStarted.WhileSubscribed(5_000)

    val products = dao.observeProducts().stateIn(viewModelScope, sharing, emptyList())
    val paymentMethods = dao.observePaymentMethods().stateIn(viewModelScope, sharing, emptyList())
    val menuPositions = dao.observeMenuPositions().stateIn(viewModelScope, sharing, emptyList())
    val receiptDocuments = dao.observeReceiptDocuments().stateIn(viewModelScope, sharing, emptyList())
    val expenseDocuments = dao.observeExpenseDocuments().stateIn(viewModelScope, sharing, emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    private val _pendingImport = MutableStateFlow<TransferFile?>(null)
    val pendingImport = _pendingImport.asStateFlow()

    fun loadImport(uri: Uri, route: String) {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                }
                transfer.parse(json, TransferSection.fromRoute(route))
            }.onSuccess { _pendingImport.value = it }
                .onFailure { _messages.emit(it.message ?: "Не удалось открыть файл") }
        }
    }

    fun dismissImport() { _pendingImport.value = null }

    fun applyImport(selected: Set<String>) {
        val file = _pendingImport.value ?: return
        viewModelScope.launch {
            runCatching { transfer.import(file, selected) }
                .onSuccess { _pendingImport.value = null; _messages.emit("Импорт завершён") }
                .onFailure { _messages.emit(it.message ?: "Не удалось импортировать данные") }
        }
    }

    fun exportTo(uri: Uri, route: String, selected: Set<String>) {
        viewModelScope.launch {
            runCatching {
                val json = transfer.export(TransferSection.fromRoute(route), selected)
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(json) }
                }
            }.onSuccess { _messages.emit("Экспорт завершён") }
                .onFailure { _messages.emit(it.message ?: "Не удалось экспортировать данные") }
        }
    }

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

    fun updateMenuPosition(id: Long, name: String) = launchAction("Позиция меню обновлена") {
        require(name.isNotBlank()) { "Укажите название позиции меню" }
        dao.updateMenuPosition(id, name.trim())
    }

    fun deleteMenuPosition(id: Long) = launchAction("Позиция меню удалена") {
        dao.deleteMenuPosition(id)
    }

    fun setIngredient(menuId: Long, productId: Long, quantity: String) = launchAction("Состав сохранён") {
        val normalized = positiveDecimal(quantity)
        val existing = dao.getIngredient(menuId, productId)
        dao.upsertIngredient(MenuIngredient(menuId, productId, normalized, guid = existing?.guid ?: java.util.UUID.randomUUID().toString()))
    }

    fun createReceiptDocument() = launchAction("Дата прихода создана") {
        dao.insertReceiptDocument(ReceiptDocument(date = LocalDateTime.now().toString()))
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

    fun updateReceiptItem(
        itemId: Long,
        documentId: Long,
        productId: Long,
        quantity: String,
        price: String,
        totalCost: String,
    ) = launchAction("Запись прихода обновлена") {
        dao.updateReceiptItem(receiptItem(itemId, documentId, productId, quantity, price, totalCost))
    }

    fun createExpenseDocument() = launchAction("Дата расхода создана") {
        dao.insertExpenseDocument(ExpenseDocument(date = LocalDateTime.now().toString()))
    }

    fun addExpenseItem(documentId: Long, menuId: Long, quantity: String, paymentMethodId: Long, comment: String) =
        launchAction("Расход проведён по FIFO") {
            dao.addExpenseWithFifo(ExpenseItem(
                documentId = documentId,
                menuPositionId = menuId,
                quantity = positiveDecimal(quantity),
                paymentMethodId = paymentMethodId,
                comment = comment.trim(),
            ))
        }

    fun updateExpenseItem(itemId: Long, documentId: Long, menuId: Long, quantity: String, paymentMethodId: Long, comment: String) =
        launchAction("Запись расхода обновлена") {
            dao.updateExpenseWithFifo(ExpenseItem(
                id = itemId,
                documentId = documentId,
                menuPositionId = menuId,
                quantity = positiveDecimal(quantity),
                paymentMethodId = paymentMethodId,
                comment = comment.trim(),
            ))
        }

    private fun receiptItem(
        id: Long,
        documentId: Long,
        productId: Long,
        quantity: String,
        price: String,
        totalCost: String,
    ): ReceiptItem {
        val normalizedQuantity = positiveDecimal(quantity)
        require(price.isNotBlank() || totalCost.isNotBlank()) {
            "Укажите цену за единицу или общую стоимость"
        }
        val quantityDecimal = normalizedQuantity.toBigDecimal()
        val priceMinor = if (price.isNotBlank()) parseMoney(price, "цену")
            else divideMoney(parseMoney(totalCost, "общую стоимость"), quantityDecimal)
        val totalCostMinor = if (totalCost.isNotBlank()) parseMoney(totalCost, "общую стоимость")
            else multiplyMoney(priceMinor, quantityDecimal)
        return ReceiptItem(id, documentId, productId, normalizedQuantity, priceMinor, totalCostMinor)
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
