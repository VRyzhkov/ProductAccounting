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
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "product-accounting.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE expense_items ADD COLUMN comment TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val tables = listOf(
                    "products",
                    "payment_methods",
                    "receipt_documents",
                    "receipt_items",
                    "menu_positions",
                    "menu_ingredients",
                    "expense_documents",
                    "expense_items",
                    "expense_allocations",
                )
                tables.forEach { table ->
                    db.execSQL("ALTER TABLE $table ADD COLUMN guid TEXT NOT NULL DEFAULT ''")
                    db.execSQL(
                        """
                        UPDATE $table SET guid =
                            lower(hex(randomblob(4))) || '-' ||
                            lower(hex(randomblob(2))) || '-4' ||
                            substr(lower(hex(randomblob(2))), 2) || '-' ||
                            substr('89ab', abs(random()) % 4 + 1, 1) ||
                            substr(lower(hex(randomblob(2))), 2) || '-' ||
                            lower(hex(randomblob(6)))
                        """.trimIndent()
                    )
                    db.execSQL("CREATE UNIQUE INDEX index_${table}_guid ON $table(guid)")
                }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE menu_positions ADD COLUMN price INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
