package ru.productaccounting

import android.app.Application
import ru.productaccounting.data.AppDatabase

class ProductAccountingApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
}
