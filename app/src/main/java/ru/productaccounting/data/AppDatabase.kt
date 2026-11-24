package ru.productaccounting.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "product-accounting.db",
        ).addMigrations(MIGRATION_1_2).build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE receipt_items ADD COLUMN totalCostMinor INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE receipt_items SET totalCostMinor = " +
                        "CAST(ROUND(unitPriceMinor * CAST(quantity AS REAL)) AS INTEGER)"
                )
            }
        }
    }
}
