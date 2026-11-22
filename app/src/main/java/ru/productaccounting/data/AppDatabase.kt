package ru.productaccounting.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Product::class,
        PaymentMethod::class,
        ReceiptDocument::class,
        ReceiptItem::class,
        MenuPosition::class,
        MenuIngredient::class,
        ExpenseDocument::class,
        ExpenseItem::class,
        ExpenseAllocation::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "product-accounting.db",
        ).build()
    }
}
