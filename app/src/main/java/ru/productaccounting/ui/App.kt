package ru.productaccounting.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.flowOf
import ru.productaccounting.MainViewModel
import ru.productaccounting.data.ExpenseItem
import ru.productaccounting.data.MenuIngredient
import ru.productaccounting.data.MenuPosition
import ru.productaccounting.data.PaymentMethod
import ru.productaccounting.data.Product
import ru.productaccounting.data.ReceiptItem
import java.math.BigDecimal
import java.math.RoundingMode

private data class Destination(val route: String, val title: String, val icon: ImageVector)
private enum class ReceiptPriceSource { UNIT, TOTAL }

private val destinations = listOf(
    Destination("products", "Товары", Icons.Default.Inventory2),
    Destination("receipts", "Приход", Icons.Default.SouthWest),
    Destination("expenses", "Расход", Icons.Default.NorthEast),
    Destination("menu", "Меню", Icons.AutoMirrored.Filled.MenuBook),
    Destination("payments", "Оплата", Icons.Default.CreditCard),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductAccountingApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: destinations.first().route
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(destinations.first { it.route == currentRoute }.title) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, destination.title) },
                        label = { Text(destination.title) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = "products", Modifier.padding(padding)) {
            composable("products") { ProductsScreen(viewModel) }
            composable("receipts") { ReceiptsScreen(viewModel) }
            composable("expenses") { ExpensesScreen(viewModel) }
            composable("menu") { MenuScreen(viewModel) }
            composable("payments") { PaymentsScreen(viewModel) }
        }
    }
}

@Composable
private fun ProductsScreen(vm: MainViewModel) {
    val products by vm.products.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var editedProduct by remember { mutableStateOf<Product?>(null) }
    EntityList(
        emptyText = null,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        floatingAction = { FloatingActionButton(onClick = {
            editedProduct = null
            showDialog = true
        }) { Icon(Icons.Default.Add, "Добавить") } },
    ) {
        val sortedProducts = products.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        items(sortedProducts, key = { it.id }) { product ->
            ListCard(product.name, "Единица измерения: ${product.unit}") {
                editedProduct = product
                showDialog = true
            }
        }
    }
    if (showDialog) {
        val product = editedProduct
        TwoFieldDialog(
            title = if (product == null) "Новый товар" else "Редактирование товара",
            firstLabel = "Название",
            secondLabel = "Единица измерения",
            initialFirst = product?.name.orEmpty(),
            initialSecond = product?.unit.orEmpty(),
            onDismiss = { showDialog = false },
        ) { name, unit ->
            if (product == null) vm.addProduct(name, unit) else vm.updateProduct(product.id, name, unit)
            showDialog = false
        }
    }
}

@Composable
private fun PaymentsScreen(vm: MainViewModel) {
    val methods by vm.paymentMethods.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    EntityList(
        emptyText = "Добавьте первый способ оплаты",
        floatingAction = { FloatingActionButton(onClick = { showDialog = true }) { Icon(Icons.Default.Add, "Добавить") } },
    ) {
        items(methods, key = { it.id }) { method -> ListCard("№${method.id}  ${method.name}") }
    }
    if (showDialog) {
        OneFieldDialog("Новый способ оплаты", "Название", onDismiss = { showDialog = false }) {
            vm.addPaymentMethod(it)
            showDialog = false
        }
    }
}

@Composable
private fun ReceiptsScreen(vm: MainViewModel) {
    val documents by vm.receiptDocuments.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    var selectedId by remember { mutableLongStateOf(0) }
    var showLineDialog by remember { mutableStateOf(false) }
    val linesFlow = remember(selectedId) { if (selectedId == 0L) flowOf(emptyList()) else vm.receiptItems(selectedId) }
    val lines by linesFlow.collectAsStateWithLifecycle(emptyList())

    DocumentScreen(
        emptyText = "Создайте вкладку прихода. Дата зафиксируется автоматически.",
        documents = documents.map { Triple(it.id, it.date, it.itemCount) },
        selectedId = selectedId,
        onSelect = { selectedId = it },
        onCreate = vm::createReceiptDocument,
        addEnabled = selectedId != 0L && products.isNotEmpty(),
        onAddLine = { showLineDialog = true },
    ) {
        lines.forEach { line ->
            val product = products.firstOrNull { it.id == line.productId }
            ListCard(
                product?.name ?: "Товар №${line.productId}",
                "${line.quantity} ${product?.unit.orEmpty()} · " +
                    "${formatMoney(line.unitPriceMinor)} за ед. · ${formatMoney(line.totalCostMinor)} всего",
            )
        }
    }
    if (showLineDialog) {
        ReceiptLineDialog(products, onDismiss = { showLineDialog = false }) { productId, quantity, price, totalCost ->
            vm.addReceiptItem(selectedId, productId, quantity, price, totalCost)
            showLineDialog = false
        }
    }
}

@Composable
private fun MenuScreen(vm: MainViewModel) {
    val positions by vm.menuPositions.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    var selectedId by remember { mutableLongStateOf(0) }
    var showPositionDialog by remember { mutableStateOf(false) }
    var showIngredientDialog by remember { mutableStateOf(false) }
    val ingredientFlow = remember(selectedId) { if (selectedId == 0L) flowOf(emptyList()) else vm.ingredients(selectedId) }
    val ingredients by ingredientFlow.collectAsStateWithLifecycle(emptyList())

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showPositionDialog = true }) { Icon(Icons.Default.Add, null); Text(" Позиция") }
            Button(
                enabled = selectedId != 0L && products.isNotEmpty(),
                onClick = { showIngredientDialog = true },
            ) { Text("Добавить товар") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(positions, key = { "menu-${it.id}" }) { position ->
                SelectableCard("№${position.id}  ${position.name}", position.id == selectedId) { selectedId = position.id }
            }
            if (selectedId != 0L) {
                item { Text("Состав", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(ingredients, key = { "ingredient-${it.productId}" }) { ingredient ->
                    val product = products.firstOrNull { it.id == ingredient.productId }
                    ListCard(product?.name ?: "Товар №${ingredient.productId}", "${ingredient.quantity} ${product?.unit.orEmpty()}")
                }
            }
        }
    }
    if (showPositionDialog) {
        OneFieldDialog("Новая позиция меню", "Название", onDismiss = { showPositionDialog = false }) {
            vm.addMenuPosition(it); showPositionDialog = false
        }
    }
    if (showIngredientDialog) {
        IngredientDialog(products, onDismiss = { showIngredientDialog = false }) { productId, quantity ->
            vm.setIngredient(selectedId, productId, quantity); showIngredientDialog = false
        }
    }
}

@Composable
private fun ExpensesScreen(vm: MainViewModel) {
    val documents by vm.expenseDocuments.collectAsStateWithLifecycle()
    val positions by vm.menuPositions.collectAsStateWithLifecycle()
    val methods by vm.paymentMethods.collectAsStateWithLifecycle()
    var selectedId by remember { mutableLongStateOf(0) }
    var showLineDialog by remember { mutableStateOf(false) }
    val linesFlow = remember(selectedId) { if (selectedId == 0L) flowOf(emptyList()) else vm.expenseItems(selectedId) }
    val lines by linesFlow.collectAsStateWithLifecycle(emptyList())

    DocumentScreen(
        emptyText = "Создайте вкладку расхода. Дата зафиксируется автоматически.",
        documents = documents.map { Triple(it.id, it.date, it.itemCount) },
        selectedId = selectedId,
        onSelect = { selectedId = it },
        onCreate = vm::createExpenseDocument,
        addEnabled = selectedId != 0L && positions.isNotEmpty() && methods.isNotEmpty(),
        onAddLine = { showLineDialog = true },
    ) {
        lines.forEach { line ->
            val menu = positions.firstOrNull { it.id == line.menuPositionId }?.name ?: "Позиция №${line.menuPositionId}"
            val payment = methods.firstOrNull { it.id == line.paymentMethodId }?.name ?: "Оплата №${line.paymentMethodId}"
            ListCard(menu, "Количество: ${line.quantity} · $payment")
        }
    }
    if (showLineDialog) {
        ExpenseLineDialog(positions, methods, onDismiss = { showLineDialog = false }) { menuId, quantity, paymentId ->
            vm.addExpenseItem(selectedId, menuId, quantity, paymentId)
            showLineDialog = false
        }
    }
}

@Composable
private fun EntityList(
    emptyText: String?,
    floatingAction: @Composable () -> Unit,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Scaffold(floatingActionButton = floatingAction) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (emptyText != null) item { Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}

@Composable
private fun DocumentScreen(
    emptyText: String,
    documents: List<Triple<Long, String, Int>>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    onCreate: () -> Unit,
    addEnabled: Boolean,
    onAddLine: () -> Unit,
    lines: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Новая вкладка") })
            Button(enabled = addEnabled, onClick = onAddLine) { Text("Добавить строку") }
        }
        Spacer(Modifier.height(12.dp))
        if (documents.isEmpty()) Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(documents, key = { "doc-${it.first}" }) { document ->
                SelectableCard("${document.second} · вкладка №${document.first}", document.first == selectedId, "Строк: ${document.third}") {
                    onSelect(document.first)
                }
            }
            if (selectedId != 0L) {
                item { Text("Содержимое вкладки", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { lines() } }
            }
        }
    }
}

@Composable
private fun ListCard(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null) {
    val modifier = if (onClick == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clickable(onClick = onClick)
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SelectableCard(title: String, selected: Boolean, subtitle: String? = null, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle)
        }
    }
}

@Composable
private fun OneFieldDialog(title: String, label: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun TwoFieldDialog(
    title: String,
    firstLabel: String,
    secondLabel: String,
    onDismiss: () -> Unit,
    initialFirst: String = "",
    initialSecond: String = "",
    onSave: (String, String) -> Unit,
) {
    var first by remember(initialFirst) { mutableStateOf(initialFirst) }
    var second by remember(initialSecond) { mutableStateOf(initialSecond) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(first, { first = it }, label = { Text(firstLabel) }, singleLine = true)
            OutlinedTextField(second, { second = it }, label = { Text(secondLabel) }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { onSave(first, second) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ReceiptLineDialog(
    products: List<Product>,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, String) -> Unit,
) {
    var productId by remember { mutableLongStateOf(products.first().id) }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var totalCost by remember { mutableStateOf("") }
    var priceSource by remember { mutableStateOf<ReceiptPriceSource?>(null) }

    fun recalculate() {
        val parsedQuantity = quantity.decimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: return
        when (priceSource) {
            ReceiptPriceSource.UNIT -> price.decimalOrNull()?.let {
                totalCost = (it * parsedQuantity).moneyInput()
            }
            ReceiptPriceSource.TOTAL -> totalCost.decimalOrNull()?.let {
                price = it.divide(parsedQuantity, 2, RoundingMode.HALF_UP).moneyInput()
            }
            null -> Unit
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Строка прихода") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceField("Товар", products, productId, { it.id }, { it.name }) { productId = it }
            OutlinedTextField(quantity, { quantity = it; recalculate() }, label = { Text("Количество") }, singleLine = true)
            OutlinedTextField(price, {
                price = it
                priceSource = ReceiptPriceSource.UNIT
                recalculate()
            }, label = { Text("Цена за единицу") }, singleLine = true)
            OutlinedTextField(totalCost, {
                totalCost = it
                priceSource = ReceiptPriceSource.TOTAL
                recalculate()
            }, label = { Text("Общая стоимость") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { onSave(productId, quantity, price, totalCost) }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun String.decimalOrNull(): BigDecimal? = trim().replace(',', '.').toBigDecimalOrNull()

private fun BigDecimal.moneyInput(): String = setScale(2, RoundingMode.HALF_UP).toPlainString()

@Composable
private fun IngredientDialog(products: List<Product>, onDismiss: () -> Unit, onSave: (Long, String) -> Unit) {
    var productId by remember { mutableLongStateOf(products.first().id) }
    var quantity by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Товар в составе") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceField("Товар", products, productId, { it.id }, { it.name }) { productId = it }
            OutlinedTextField(quantity, { quantity = it }, label = { Text("Количество на одну позицию") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { onSave(productId, quantity) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ExpenseLineDialog(positions: List<MenuPosition>, methods: List<PaymentMethod>, onDismiss: () -> Unit, onSave: (Long, String, Long) -> Unit) {
    var menuId by remember { mutableLongStateOf(positions.first().id) }
    var paymentId by remember { mutableLongStateOf(methods.first().id) }
    var quantity by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Строка расхода") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceField("Позиция меню", positions, menuId, { it.id }, { it.name }) { menuId = it }
            OutlinedTextField(quantity, { quantity = it }, label = { Text("Количество") }, singleLine = true)
            ChoiceField("Способ оплаты", methods, paymentId, { it.id }, { it.name }) { paymentId = it }
        } },
        confirmButton = { TextButton(onClick = { onSave(menuId, quantity, paymentId) }) { Text("Провести") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun <T> ChoiceField(label: String, values: List<T>, selectedId: Long, id: (T) -> Long, name: (T) -> String, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(values.firstOrNull { id(it) == selectedId }?.let(name) ?: "Выберите")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem(text = { Text(name(value)) }, onClick = { onSelect(id(value)); expanded = false })
            }
        }
    }
}

private fun formatMoney(value: Long): String = "%d,%02d ₽".format(value / 100, value % 100)
