package ru.productaccounting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
abstract class AppDao {
    @Query("SELECT * FROM products") abstract suspend fun allProducts(): List<Product>
    @Query("SELECT * FROM payment_methods") abstract suspend fun allPaymentMethods(): List<PaymentMethod>
    @Query("SELECT * FROM receipt_documents") abstract suspend fun allReceiptDocuments(): List<ReceiptDocument>
    @Query("SELECT * FROM receipt_items") abstract suspend fun allReceiptItems(): List<ReceiptItem>
    @Query("SELECT * FROM menu_positions") abstract suspend fun allMenuPositions(): List<MenuPosition>
    @Query("SELECT * FROM menu_ingredients") abstract suspend fun allMenuIngredients(): List<MenuIngredient>
    @Query("SELECT * FROM expense_documents") abstract suspend fun allExpenseDocuments(): List<ExpenseDocument>
    @Query("SELECT * FROM expense_documents WHERE id = :id") abstract suspend fun getExpenseDocument(id: Long): ExpenseDocument
    @Query("SELECT * FROM expense_items") abstract suspend fun allExpenseItems(): List<ExpenseItem>
    @Query("SELECT * FROM expense_allocations") abstract suspend fun allExpenseAllocations(): List<ExpenseAllocation>

    @Query("SELECT * FROM products WHERE guid = :guid") abstract suspend fun productByGuid(guid: String): Product?
    @Query("SELECT * FROM payment_methods WHERE guid = :guid") abstract suspend fun paymentByGuid(guid: String): PaymentMethod?
    @Query("SELECT * FROM receipt_documents WHERE guid = :guid") abstract suspend fun receiptDocumentByGuid(guid: String): ReceiptDocument?
    @Query("SELECT * FROM receipt_items WHERE guid = :guid") abstract suspend fun receiptItemByGuid(guid: String): ReceiptItem?
    @Query("SELECT * FROM menu_positions WHERE guid = :guid") abstract suspend fun menuPositionByGuid(guid: String): MenuPosition?
    @Query("SELECT * FROM menu_ingredients WHERE guid = :guid") abstract suspend fun menuIngredientByGuid(guid: String): MenuIngredient?
    @Query("SELECT * FROM expense_documents WHERE guid = :guid") abstract suspend fun expenseDocumentByGuid(guid: String): ExpenseDocument?
    @Query("SELECT * FROM expense_items WHERE guid = :guid") abstract suspend fun expenseItemByGuid(guid: String): ExpenseItem?
    @Query("SELECT * FROM expense_allocations WHERE guid = :guid") abstract suspend fun expenseAllocationByGuid(guid: String): ExpenseAllocation?

    @Upsert abstract suspend fun saveProduct(value: Product)
    @Upsert abstract suspend fun savePaymentMethod(value: PaymentMethod)
    @Upsert abstract suspend fun saveReceiptDocument(value: ReceiptDocument)
    @Upsert abstract suspend fun saveReceiptItem(value: ReceiptItem)
    @Upsert abstract suspend fun saveMenuPosition(value: MenuPosition)
    @Upsert abstract suspend fun saveMenuIngredient(value: MenuIngredient)
    @Upsert abstract suspend fun saveExpenseDocument(value: ExpenseDocument)
    @Upsert abstract suspend fun saveExpenseItem(value: ExpenseItem)
    @Upsert abstract suspend fun saveExpenseAllocation(value: ExpenseAllocation)

    @Query("SELECT * FROM products ORDER BY id")
    abstract fun observeProducts(): Flow<List<Product>>

    @Insert
    abstract suspend fun insertProduct(product: Product): Long

    @Query("UPDATE products SET name = :name, unit = :unit WHERE id = :id")
    abstract suspend fun updateProduct(id: Long, name: String, unit: String)

    @Query("SELECT * FROM payment_methods ORDER BY id")
    abstract fun observePaymentMethods(): Flow<List<PaymentMethod>>

    @Insert
    abstract suspend fun insertPaymentMethod(method: PaymentMethod): Long

    @Query("UPDATE payment_methods SET name = :name, reportValueType = :reportValueType WHERE id = :id")
    abstract suspend fun updatePaymentMethod(id: Long, name: String, reportValueType: String)

    @Query("SELECT * FROM menu_positions ORDER BY id")
    abstract fun observeMenuPositions(): Flow<List<MenuPosition>>

    @Insert
    abstract suspend fun insertMenuPosition(position: MenuPosition): Long

    @Query("UPDATE menu_positions SET name = :name, price = :price WHERE id = :id")
    abstract suspend fun updateMenuPosition(id: Long, name: String, price: Long)

    @Query("SELECT COUNT(*) FROM expense_items WHERE menuPositionId = :id")
    abstract suspend fun countExpensesForMenuPosition(id: Long): Int

    @Query("DELETE FROM menu_positions WHERE id = :id")
    abstract suspend fun deleteMenuPositionRow(id: Long)

    @Transaction
    open suspend fun deleteMenuPosition(id: Long) {
        require(countExpensesForMenuPosition(id) == 0) {
            "Нельзя удалить позицию, которая уже используется в расходах"
        }
        deleteMenuPositionRow(id)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertIngredient(ingredient: MenuIngredient)

    @Query("SELECT * FROM menu_ingredients WHERE menuPositionId = :menuId AND productId = :productId")
    abstract suspend fun getIngredient(menuId: Long, productId: Long): MenuIngredient?

    @Query("SELECT * FROM menu_ingredients WHERE menuPositionId = :menuId ORDER BY productId")
    abstract fun observeIngredients(menuId: Long): Flow<List<MenuIngredient>>

    @Query("SELECT * FROM menu_ingredients WHERE menuPositionId = :menuId ORDER BY productId")
    abstract suspend fun getIngredients(menuId: Long): List<MenuIngredient>

    @Query("""
        SELECT d.id, d.guid, d.date, COUNT(i.id) AS itemCount
        FROM receipt_documents d LEFT JOIN receipt_items i ON i.documentId = d.id
        GROUP BY d.id ORDER BY d.date DESC, d.id DESC
    """)
    abstract fun observeReceiptDocuments(): Flow<List<ReceiptDocumentSummary>>

    @Insert
    abstract suspend fun insertReceiptDocument(document: ReceiptDocument): Long

    @Insert
    abstract suspend fun insertReceiptItem(item: ReceiptItem): Long

    @Query("SELECT * FROM receipt_items WHERE id = :id")
    abstract suspend fun getReceiptItem(id: Long): ReceiptItem

    @Query("SELECT CAST(TOTAL(quantity) AS TEXT) FROM expense_allocations WHERE receiptItemId = :receiptItemId")
    abstract suspend fun getAllocatedQuantity(receiptItemId: Long): String?

    @Query("UPDATE receipt_items SET productId = :productId, quantity = :quantity, unitPriceMinor = :unitPriceMinor, totalCostMinor = :totalCostMinor WHERE id = :id")
    abstract suspend fun updateReceiptItemValues(
        id: Long,
        productId: Long,
        quantity: String,
        unitPriceMinor: Long,
        totalCostMinor: Long,
    )

    @Transaction
    open suspend fun updateReceiptItem(item: ReceiptItem) {
        val oldItem = getReceiptItem(item.id)
        val allocated = getAllocatedQuantity(item.id).decimalOrZero()
        require(oldItem.productId == item.productId || allocated == BigDecimal.ZERO) {
            "Нельзя изменить товар: часть этой партии уже списана"
        }
        require(item.quantity.decimal() >= allocated) {
            "Количество не может быть меньше уже списанного: $allocated"
        }
        updateReceiptItemValues(
            item.id,
            item.productId,
            item.quantity,
            item.unitPriceMinor,
            item.totalCostMinor,
        )
    }

    @Query("SELECT * FROM receipt_items WHERE documentId = :documentId ORDER BY id")
    abstract fun observeReceiptItems(documentId: Long): Flow<List<ReceiptItem>>

    @Query("""
        SELECT d.id, d.guid, d.date, COUNT(i.id) AS itemCount
        FROM expense_documents d LEFT JOIN expense_items i ON i.documentId = d.id
        GROUP BY d.id ORDER BY d.date DESC, d.id DESC
    """)
    abstract fun observeExpenseDocuments(): Flow<List<ExpenseDocumentSummary>>

    @Insert
    abstract suspend fun insertExpenseDocument(document: ExpenseDocument): Long

    @Insert
    abstract suspend fun insertExpenseItem(item: ExpenseItem): Long

    @Query("UPDATE expense_items SET menuPositionId = :menuPositionId, quantity = :quantity, paymentMethodId = :paymentMethodId, comment = :comment, ingredientChoices = :ingredientChoices WHERE id = :id")
    abstract suspend fun updateExpenseItemValues(id: Long, menuPositionId: Long, quantity: String, paymentMethodId: Long, comment: String, ingredientChoices: String)

    @Query("DELETE FROM expense_allocations WHERE expenseItemId = :expenseItemId")
    abstract suspend fun deleteAllocations(expenseItemId: Long)

    @Insert
    abstract suspend fun insertAllocation(allocation: ExpenseAllocation)

    @Query("SELECT * FROM expense_items WHERE documentId = :documentId ORDER BY id")
    abstract fun observeExpenseItems(documentId: Long): Flow<List<ExpenseItem>>

    @Query("SELECT * FROM expense_items WHERE documentId = :documentId ORDER BY id")
    abstract suspend fun getExpenseItems(documentId: Long): List<ExpenseItem>

    @Query("""
        SELECT a.quantity, r.unitPriceMinor
        FROM expense_allocations a
        JOIN receipt_items r ON r.id = a.receiptItemId
        WHERE a.expenseItemId = :expenseItemId
    """)
    abstract suspend fun getExpenseAllocationCosts(expenseItemId: Long): List<ExpenseAllocationCost>

    @Query("""
        SELECT r.id AS receiptItemId, r.quantity,
               (SELECT CAST(TOTAL(a.quantity) AS TEXT) FROM expense_allocations a WHERE a.receiptItemId = r.id) AS allocated
        FROM receipt_items r
        JOIN receipt_documents d ON d.id = r.documentId
        WHERE r.productId = :productId
        ORDER BY d.date, r.id
    """)
    abstract suspend fun getBatches(productId: Long): List<ReceiptBatchBalance>

    @Transaction
    open suspend fun addExpenseWithFifo(item: ExpenseItem): Long {
        return allocateExpense(item, insertExpenseItem(item))
    }

    @Transaction
    open suspend fun updateExpenseWithFifo(item: ExpenseItem) {
        deleteAllocations(item.id)
        updateExpenseItemValues(item.id, item.menuPositionId, item.quantity, item.paymentMethodId, item.comment, item.ingredientChoices)
        allocateExpense(item, item.id)
    }

    private suspend fun allocateExpense(item: ExpenseItem, expenseId: Long): Long {
        val ingredients = getIngredients(item.menuPositionId)
        require(ingredients.isNotEmpty()) { "У позиции меню не задан состав" }
        val multiplier = item.quantity.decimal()
        val choices = item.ingredientChoices.split(',').mapNotNull { pair ->
            val parts = pair.split(':', limit = 2)
            parts.takeIf { it.size == 2 }?.let { it[0] to it[1].toLongOrNull() }?.takeIf { it.second != null }
        }.associate { it.first to it.second!! }
        val selectedIngredients = ingredients.groupBy { it.choiceGroup }.values.map { group ->
            val selectedId = choices[group.first().choiceGroup] ?: group.first().productId
            group.firstOrNull { it.productId == selectedId }
                ?: error("Выбранный товар не входит в состав позиции меню")
        }
        val requirements = selectedIngredients.groupingBy { it.productId }.fold(BigDecimal.ZERO) { total, ingredient ->
            total + ingredient.quantity.decimal() * multiplier
        }

        // Validate all balances before writing anything. The Room transaction rolls back on failure.
        requirements.forEach { (productId, required) ->
            val available = getBatches(productId).sumOf { it.quantity.decimal() - it.allocated.decimalOrZero() }
            require(available >= required) { "Недостаточно товара №$productId: требуется $required, доступно $available" }
        }

        requirements.forEach { (productId, required) ->
            var remaining = required
            for (batch in getBatches(productId)) {
                if (remaining <= BigDecimal.ZERO) break
                val available = batch.quantity.decimal() - batch.allocated.decimalOrZero()
                if (available <= BigDecimal.ZERO) continue
                val taken = available.min(remaining)
                insertAllocation(
                    ExpenseAllocation(
                        expenseItemId = expenseId,
                        receiptItemId = batch.receiptItemId,
                        productId = productId,
                        quantity = taken.stripTrailingZeros().toPlainString(),
                    )
                )
                remaining -= taken
            }
        }
        return expenseId
    }
}

private fun String.decimal(): BigDecimal = replace(',', '.').toBigDecimal()
private fun String?.decimalOrZero(): BigDecimal = if (isNullOrBlank()) BigDecimal.ZERO else decimal()
