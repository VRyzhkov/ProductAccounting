package ru.productaccounting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
abstract class AppDao {
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

    @Query("SELECT * FROM menu_positions ORDER BY id")
    abstract fun observeMenuPositions(): Flow<List<MenuPosition>>

    @Insert
    abstract suspend fun insertMenuPosition(position: MenuPosition): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertIngredient(ingredient: MenuIngredient)

    @Query("SELECT * FROM menu_ingredients WHERE menuPositionId = :menuId ORDER BY productId")
    abstract fun observeIngredients(menuId: Long): Flow<List<MenuIngredient>>

    @Query("SELECT * FROM menu_ingredients WHERE menuPositionId = :menuId ORDER BY productId")
    abstract suspend fun getIngredients(menuId: Long): List<MenuIngredient>

    @Query("""
        SELECT d.id, d.date, COUNT(i.id) AS itemCount
        FROM receipt_documents d LEFT JOIN receipt_items i ON i.documentId = d.id
        GROUP BY d.id ORDER BY d.date DESC, d.id DESC
    """)
    abstract fun observeReceiptDocuments(): Flow<List<ReceiptDocumentSummary>>

    @Insert
    abstract suspend fun insertReceiptDocument(document: ReceiptDocument): Long

    @Insert
    abstract suspend fun insertReceiptItem(item: ReceiptItem): Long

    @Query("SELECT * FROM receipt_items WHERE documentId = :documentId ORDER BY id")
    abstract fun observeReceiptItems(documentId: Long): Flow<List<ReceiptItem>>

    @Query("""
        SELECT d.id, d.date, COUNT(i.id) AS itemCount
        FROM expense_documents d LEFT JOIN expense_items i ON i.documentId = d.id
        GROUP BY d.id ORDER BY d.date DESC, d.id DESC
    """)
    abstract fun observeExpenseDocuments(): Flow<List<ExpenseDocumentSummary>>

    @Insert
    abstract suspend fun insertExpenseDocument(document: ExpenseDocument): Long

    @Insert
    abstract suspend fun insertExpenseItem(item: ExpenseItem): Long

    @Insert
    abstract suspend fun insertAllocation(allocation: ExpenseAllocation)

    @Query("SELECT * FROM expense_items WHERE documentId = :documentId ORDER BY id")
    abstract fun observeExpenseItems(documentId: Long): Flow<List<ExpenseItem>>

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
        val ingredients = getIngredients(item.menuPositionId)
        require(ingredients.isNotEmpty()) { "У позиции меню не задан состав" }
        val multiplier = item.quantity.decimal()
        val requirements = ingredients.associate { it.productId to it.quantity.decimal() * multiplier }

        // Validate all balances before writing anything. The Room transaction rolls back on failure.
        requirements.forEach { (productId, required) ->
            val available = getBatches(productId).sumOf { it.quantity.decimal() - it.allocated.decimalOrZero() }
            require(available >= required) { "Недостаточно товара №$productId: требуется $required, доступно $available" }
        }

        val expenseId = insertExpenseItem(item)
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
