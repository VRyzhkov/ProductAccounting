package ru.productaccounting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.productaccounting.ui.ProductAccountingApp
import ru.productaccounting.ui.theme.ProductAccountingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProductAccountingTheme {
                ProductAccountingApp(viewModel())
            }
        }
    }
}
