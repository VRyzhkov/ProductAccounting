package ru.productaccounting.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

private fun newGuid(): String = UUID.randomUUID().toString()

@Entity(tableName = "products", indices = [Index(value = ["name"], unique = true), Index(value = ["guid"], unique = true)])
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String,
    @ColumnInfo(defaultValue = "''") val guid: String = newGuid(),
)

@Entity(tableName = "payment_methods", indices = [Index(value = ["name"], unique = true), Index(value = ["guid"], unique = true)])
data class PaymentMethod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "'SALE'") val reportValueType: String = "SALE",
    @ColumnInfo(defaultValue = "''") val guid: String = newGuid(),
)

@Entity(tableName = "receipt_documents", indices = [Index(value = ["guid"], unique = true)])
data class ReceiptDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    @ColumnInfo(defaultValue = "''") val guid: String = newGuid(),
)

@Entity(
    tableName = "receipt_items",
    foreignKeys = [
        ForeignKey(ReceiptDocument::class, ["id"], ["documentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Product::class, ["id"], ["productId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("documentId"), Index("productId"), Index(value = ["guid"], unique = true)],
)
data class ReceiptItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val productId: Long,
    /** Decimal value stored as text to preserve exact user-entered quantity. */
    val quantity: String,
    /** Price in kopecks (or another minimum currency unit). */
    val unitPriceMinor: Long,
    /** Total line cost in kopecks. Kept separately to avoid losing it when unit price is rounded. */
    val totalCostMinor: Long,
    @ColumnInfo(defaultValue = "''") val guid: String = newGuid(),
)

@Entity(tableName = "menu_positions", indices = [Index(value = ["name"], unique = true), Index(value = ["guid"], unique = true)])
data class MenuPosition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Long = 0,
    @ColumnInfo(defaultValue = "''") val guid: String = newGuid(),
)

@Entity(
    tableName = "menu_ingredients",
    primaryKeys = ["menuPositionId", "productId"],
    foreignKeys = [
        ForeignKey(MenuPosition::class, ["id"], ["menuPositionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Product::class, ["id"], ["productId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("productId"), Index(value = ["guid"], unique = true)],
)
data class MenuIngredient(
    val menuPositionId: Long,
    val productId: Long,
    val quantity: String,
    @ColumnInfo(defaultValue = "''") val choiceGroup: String = newGuid(),
    @ColumnInfo(defaultValue = "''") val guid: String = newGuid(),
)

@Entity(tableName = "expense_documents", indices = [Index(value = ["guid"], unique = true)])
data class ExpenseDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    @ColumnInfo(defaultValue = "''") val guid: String = newGuid(),
)

@Entity(
    tableName = "expense_items",
    foreignKeys = [
        ForeignKey(ExpenseDocument::class, ["id"], ["documentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(MenuPosition::class, ["id"], ["menuPositionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(PaymentMethod::class, ["id"], ["paymentMethodId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("documentId"), Index("menuPositionId"), Index("paymentMethodId"), Index(value = ["guid"], unique = true)],
)
data class ExpenseItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val menuPositionId: Long,
    val quantity: String,
    val paymentMethodId: Long,
    val comment: String = "",
    /** choice group GUID to selected product id, encoded as group:id pairs. */
    @ColumnInfo(defaultValue = "''") val ingredientChoices: String = "",
    @ColumnInfo(defaultValue = "''") val guid: String = newGuid(),
)

@Entity(
    tableName = "expense_allocations",
    foreignKeys = [
        ForeignKey(ExpenseItem::class, ["id"], ["expenseItemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ReceiptItem::class, ["id"], ["receiptItemId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("expenseItemId"), Index("receiptItemId"), Index(value = ["guid"], unique = true)],
)
data class ExpenseAllocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseItemId: Long,
    val receiptItemId: Long,
    val productId: Long,
    val quantity: String,
    @ColumnInfo(defaultValue = "''") val guid: String = newGuid(),
)

data class ReceiptDocumentSummary(val id: Long, val guid: String, val date: String, val itemCount: Int)
data class ExpenseDocumentSummary(val id: Long, val guid: String, val date: String, val itemCount: Int)
data class ReceiptBatchBalance(val receiptItemId: Long, val quantity: String, val allocated: String?)
data class ExpenseAllocationCost(val quantity: String, val unitPriceMinor: Long)
