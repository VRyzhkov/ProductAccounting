package ru.productaccounting.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
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
import ru.productaccounting.data.TransferEntry
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    val products by viewModel.products.collectAsStateWithLifecycle()
    val payments by viewModel.paymentMethods.collectAsStateWithLifecycle()
    val receiptDocuments by viewModel.receiptDocuments.collectAsStateWithLifecycle()
    val menuPositions by viewModel.menuPositions.collectAsStateWithLifecycle()
    val expenseDocuments by viewModel.expenseDocuments.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    var exportEntries by remember { mutableStateOf<List<TransferEntry>?>(null) }
    var exportSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadImport(uri, currentRoute)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportTo(uri, currentRoute, exportSelection)
        exportSelection = emptySet()
    }
    fun currentEntries(): List<TransferEntry> = when (currentRoute) {
        "products" -> products.map { TransferEntry(it.guid, it.name) }
        "payments" -> payments.map { TransferEntry(it.guid, it.name) }
        "receipts" -> receiptDocuments.map { TransferEntry(it.guid, formatDocumentDate(it.date)) }
        "menu" -> menuPositions.map { TransferEntry(it.guid, it.name) }
        "expenses" -> expenseDocuments.map { TransferEntry(it.guid, formatDocumentDate(it.date)) }
        else -> emptyList()
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(destinations.first { it.route == currentRoute }.title) },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Импорт")
                    }
                    IconButton(onClick = { exportEntries = currentEntries() }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Экспорт")
                    }
                },
            )
        },
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
    exportEntries?.let { entries ->
        TransferSelectionDialog(
            title = "Экспорт",
            entries = entries,
            onDismiss = { exportEntries = null },
        ) { selected ->
            exportEntries = null
            exportSelection = selected
            exportLauncher.launch("product-accounting-$currentRoute.json")
        }
    }
    pendingImport?.let { file ->
        TransferSelectionDialog(
            title = "Импорт",
            entries = file.entries,
            onDismiss = viewModel::dismissImport,
            onConfirm = viewModel::applyImport,
        )
    }
}

@Composable
private fun TransferSelectionDialog(
    title: String,
    entries: List<TransferEntry>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selected by remember(entries) { mutableStateOf(entries.mapTo(mutableSetOf()) { it.guid }.toSet()) }
    val allSelected = entries.isNotEmpty() && selected.size == entries.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selected = if (allSelected) emptySet() else entries.mapTo(mutableSetOf()) { it.guid }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = allSelected, onCheckedChange = null)
                        Text("Выбрать все")
                    }
                }
                items(entries, key = { it.guid }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selected = selected.toMutableSet().apply {
                                if (!add(entry.guid)) remove(entry.guid)
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = entry.guid in selected, onCheckedChange = null)
                        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty()) { Text(title) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
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
            ProductListCard(product.name, product.unit) {
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
        emptyText = null,
        floatingAction = { FloatingActionButton(onClick = { showDialog = true }) { Icon(Icons.Default.Add, "Добавить") } },
    ) {
        val sortedMethods = methods.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        items(sortedMethods, key = { it.id }) { method -> ListCard(method.name) }
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
    var editedLine by remember { mutableStateOf<ReceiptItem?>(null) }
    val linesFlow = remember(selectedId) { if (selectedId == 0L) flowOf(emptyList()) else vm.receiptItems(selectedId) }
    val lines by linesFlow.collectAsStateWithLifecycle(emptyList())

    DocumentScreen(
        emptyText = "Добавьте первую дату прихода.",
        documents = documents.map { Triple(it.id, it.date, it.itemCount) },
        selectedId = selectedId,
        onSelect = { selectedId = if (selectedId == it) 0 else it },
        onCreate = vm::createReceiptDocument,
        addEnabled = selectedId != 0L && products.isNotEmpty(),
        onAddLine = { editedLine = null; showLineDialog = true },
    ) {
        lines.forEach { line ->
            val product = products.firstOrNull { it.id == line.productId }
            ListCard(
                product?.name ?: "Товар №${line.productId}",
                "${line.quantity} ${product?.unit.orEmpty()} · " +
                    "${formatMoney(line.unitPriceMinor)} за ед. · ${formatMoney(line.totalCostMinor)} всего",
            ) { editedLine = line; showLineDialog = true }
        }
    }
    if (showLineDialog) {
        val line = editedLine
        ReceiptLineDialog(products, line, onDismiss = { showLineDialog = false }) { productId, quantity, price, totalCost ->
            if (line == null) vm.addReceiptItem(selectedId, productId, quantity, price, totalCost)
            else vm.updateReceiptItem(line.id, line.documentId, productId, quantity, price, totalCost)
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
    var editedPosition by remember { mutableStateOf<MenuPosition?>(null) }
    var positionToDelete by remember { mutableStateOf<MenuPosition?>(null) }
    val ingredientFlow = remember(selectedId) { if (selectedId == 0L) flowOf(emptyList()) else vm.ingredients(selectedId) }
    val ingredients by ingredientFlow.collectAsStateWithLifecycle(emptyList())

    Scaffold(
        floatingActionButton = {
            if (selectedId == 0L || products.isNotEmpty()) {
                FloatingActionButton(onClick = {
                    if (selectedId == 0L) {
                        editedPosition = null
                        showPositionDialog = true
                    } else {
                        showIngredientDialog = true
                    }
                }) { Icon(Icons.Default.Add, if (selectedId == 0L) "Добавить позицию" else "Добавить товар") }
            }
        },
    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            val sortedPositions = positions.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            items(sortedPositions, key = { "menu-${it.id}" }) { position ->
                MenuPositionCard(
                    position = position,
                    expanded = position.id == selectedId,
                    onToggle = { selectedId = if (selectedId == position.id) 0 else position.id },
                    onEdit = { editedPosition = position; showPositionDialog = true },
                    onDelete = { positionToDelete = position },
                )
                if (position.id == selectedId) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                    ) {
                        ingredients.forEach { ingredient ->
                            val product = products.firstOrNull { it.id == ingredient.productId }
                            ListCard(product?.name ?: "Товар №${ingredient.productId}", "${ingredient.quantity} ${product?.unit.orEmpty()}")
                        }
                    }
                }
            }
        }
    }
    if (showPositionDialog) {
        val position = editedPosition
        OneFieldDialog(
            title = if (position == null) "Новая позиция меню" else "Изменение позиции",
            label = "Название",
            initialValue = position?.name.orEmpty(),
            onDismiss = { showPositionDialog = false },
        ) {
            if (position == null) vm.addMenuPosition(it) else vm.updateMenuPosition(position.id, it)
            showPositionDialog = false
        }
    }
    if (showIngredientDialog) {
        IngredientDialog(products, onDismiss = { showIngredientDialog = false }) { productId, quantity ->
            vm.setIngredient(selectedId, productId, quantity); showIngredientDialog = false
        }
    }
    positionToDelete?.let { position ->
        AlertDialog(
            onDismissRequest = { positionToDelete = null },
            title = { Text("Удалить позицию?") },
            text = { Text("Позиция «${position.name}» и её состав будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMenuPosition(position.id)
                    if (selectedId == position.id) selectedId = 0
                    positionToDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { positionToDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ExpensesScreen(vm: MainViewModel) {
    val documents by vm.expenseDocuments.collectAsStateWithLifecycle()
    val positions by vm.menuPositions.collectAsStateWithLifecycle()
    val methods by vm.paymentMethods.collectAsStateWithLifecycle()
    var selectedId by remember { mutableLongStateOf(0) }
    var showLineDialog by remember { mutableStateOf(false) }
    var editedLine by remember { mutableStateOf<ExpenseItem?>(null) }
    val linesFlow = remember(selectedId) { if (selectedId == 0L) flowOf(emptyList()) else vm.expenseItems(selectedId) }
    val lines by linesFlow.collectAsStateWithLifecycle(emptyList())

    DocumentScreen(
        emptyText = "Добавьте первую дату расхода.",
        documents = documents.map { Triple(it.id, it.date, it.itemCount) },
        selectedId = selectedId,
        onSelect = { selectedId = if (selectedId == it) 0 else it },
        onCreate = vm::createExpenseDocument,
        addEnabled = selectedId != 0L && positions.isNotEmpty() && methods.isNotEmpty(),
        onAddLine = { editedLine = null; showLineDialog = true },
    ) {
        lines.forEach { line ->
            val menu = positions.firstOrNull { it.id == line.menuPositionId }?.name ?: "Позиция №${line.menuPositionId}"
            val payment = methods.firstOrNull { it.id == line.paymentMethodId }?.name ?: "Оплата №${line.paymentMethodId}"
            val details = "Количество: ${line.quantity} · $payment" +
                if (line.comment.isBlank()) "" else " · ${line.comment}"
            ListCard(menu, details) { editedLine = line; showLineDialog = true }
        }
    }
    if (showLineDialog) {
        val line = editedLine
        ExpenseLineDialog(positions, methods, line, onDismiss = { showLineDialog = false }) { menuId, quantity, paymentId, comment ->
            if (line == null) vm.addExpenseItem(selectedId, menuId, quantity, paymentId, comment)
            else vm.updateExpenseItem(line.id, line.documentId, menuId, quantity, paymentId, comment)
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
    Scaffold(
        floatingActionButton = {
            if (selectedId == 0L || addEnabled) {
                FloatingActionButton(onClick = if (selectedId == 0L) onCreate else onAddLine) {
                    Icon(Icons.Default.Add, if (selectedId == 0L) "Добавить дату" else "Добавить запись")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            if (documents.isEmpty()) item { Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(documents, key = { "doc-${it.first}" }) { document ->
                SelectableCard(formatDocumentDate(document.second), document.first == selectedId) {
                    onSelect(document.first)
                }
                if (document.first == selectedId) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                    ) { lines() }
                }
            }
        }
    }
}

@Composable
private fun ProductListCard(title: String, unit: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "ед. изм.: $unit",
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun MenuPositionCard(
    position: MenuPosition,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = position.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Изменить")
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Удалить")
            }
        }
    }
}

@Composable
private fun OneFieldDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    initialValue: String = "",
    onSave: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
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
    initial: ReceiptItem? = null,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, String) -> Unit,
) {
    var productId by remember(initial) { mutableLongStateOf(initial?.productId ?: products.first().id) }
    var quantity by remember(initial) { mutableStateOf(initial?.quantity.orEmpty()) }
    var price by remember(initial) { mutableStateOf(initial?.unitPriceMinor?.moneyInput().orEmpty()) }
    var totalCost by remember(initial) { mutableStateOf(initial?.totalCostMinor?.moneyInput().orEmpty()) }
    var priceSource by remember(initial) {
        mutableStateOf<ReceiptPriceSource?>(if (initial == null) null else ReceiptPriceSource.UNIT)
    }

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
        title = { Text(if (initial == null) "Новая запись прихода" else "Редактирование прихода") },
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
        confirmButton = { TextButton(onClick = { onSave(productId, quantity, price, totalCost) }) { Text("Сохранить") } },
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
private fun ExpenseLineDialog(positions: List<MenuPosition>, methods: List<PaymentMethod>, initial: ExpenseItem? = null, onDismiss: () -> Unit, onSave: (Long, String, Long, String) -> Unit) {
    var menuId by remember(initial) { mutableLongStateOf(initial?.menuPositionId ?: positions.first().id) }
    var paymentId by remember(initial) { mutableLongStateOf(initial?.paymentMethodId ?: methods.first().id) }
    var quantity by remember(initial) {
        mutableLongStateOf(initial?.quantity?.toLongOrNull()?.takeIf { it in 1L..10L } ?: 1L)
    }
    var comment by remember(initial) { mutableStateOf(initial?.comment.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новая запись расхода" else "Редактирование расхода") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceField("Позиция меню", positions, menuId, { it.id }, { it.name }) { menuId = it }
            ChoiceField("Количество", (1..10).toList(), quantity, { it.toLong() }, { it.toString() }) { quantity = it }
            ChoiceField("Способ оплаты", methods, paymentId, { it.id }, { it.name }) { paymentId = it }
            OutlinedTextField(comment, { comment = it }, label = { Text("Комментарий") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = { onSave(menuId, quantity.toString(), paymentId, comment) }) { Text("Сохранить") } },
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

private fun Long.moneyInput(): String = BigDecimal.valueOf(this, 2).moneyInput()

private fun formatDocumentDate(value: String): String = runCatching {
    LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
}.recoverCatching {
    LocalDate.parse(value).atStartOfDay().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
}.getOrDefault(value)
