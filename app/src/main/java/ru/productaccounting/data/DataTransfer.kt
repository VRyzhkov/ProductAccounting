package ru.productaccounting.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

enum class TransferSection(val route: String) {
    PRODUCTS("products"), RECEIPTS("receipts"), EXPENSES("expenses"), MENU("menu"), PAYMENTS("payments");
    companion object { fun fromRoute(route: String) = entries.first { it.route == route } }
}

data class TransferEntry(val guid: String, val name: String)
data class TransferFile(val section: TransferSection, val entries: List<TransferEntry>, val json: String)

class DataTransfer(private val database: AppDatabase) {
    private val dao = database.appDao()

    suspend fun export(section: TransferSection, selected: Set<String>): String {
        val products = dao.allProducts(); val payments = dao.allPaymentMethods()
        val receiptDocs = dao.allReceiptDocuments(); val receiptItems = dao.allReceiptItems()
        val menus = dao.allMenuPositions(); val ingredients = dao.allMenuIngredients()
        val expenseDocs = dao.allExpenseDocuments(); val expenseItems = dao.allExpenseItems()
        val allocations = dao.allExpenseAllocations()

        val chosenMenus = when (section) {
            TransferSection.MENU -> menus.filter { it.guid in selected }
            TransferSection.EXPENSES -> {
                val itemIds = expenseDocs.filter { it.guid in selected }.mapTo(mutableSetOf()) { it.id }
                val menuIds = expenseItems.filter { it.documentId in itemIds }.mapTo(mutableSetOf()) { it.menuPositionId }
                menus.filter { it.id in menuIds }
            }
            else -> emptyList()
        }
        val chosenExpenseDocs = if (section == TransferSection.EXPENSES) expenseDocs.filter { it.guid in selected } else emptyList()
        val chosenExpenseDocIds = chosenExpenseDocs.mapTo(mutableSetOf()) { it.id }
        val chosenExpenseItems = expenseItems.filter { it.documentId in chosenExpenseDocIds }
        val chosenExpenseItemIds = chosenExpenseItems.mapTo(mutableSetOf()) { it.id }
        val chosenAllocations = allocations.filter { it.expenseItemId in chosenExpenseItemIds }
        val allocatedReceiptIds = chosenAllocations.mapTo(mutableSetOf()) { it.receiptItemId }
        val chosenReceiptDocs = when (section) {
            TransferSection.RECEIPTS -> receiptDocs.filter { it.guid in selected }
            TransferSection.EXPENSES -> {
                val docIds = receiptItems.filter { it.id in allocatedReceiptIds }.mapTo(mutableSetOf()) { it.documentId }
                receiptDocs.filter { it.id in docIds }
            }
            else -> emptyList()
        }
        val chosenReceiptDocIds = chosenReceiptDocs.mapTo(mutableSetOf()) { it.id }
        val chosenReceiptItems = receiptItems.filter { it.documentId in chosenReceiptDocIds }
        val chosenMenuIds = chosenMenus.mapTo(mutableSetOf()) { it.id }
        val chosenIngredients = ingredients.filter { it.menuPositionId in chosenMenuIds }
        val productIds = mutableSetOf<Long>().apply {
            if (section == TransferSection.PRODUCTS) addAll(products.filter { it.guid in selected }.map { it.id })
            addAll(chosenReceiptItems.map { it.productId }); addAll(chosenIngredients.map { it.productId })
        }
        val chosenProducts = products.filter { it.id in productIds }
        val paymentIds = chosenExpenseItems.mapTo(mutableSetOf()) { it.paymentMethodId }
        val chosenPayments = if (section == TransferSection.PAYMENTS) payments.filter { it.guid in selected }
            else payments.filter { it.id in paymentIds }

        val productGuid = products.associate { it.id to it.guid }; val paymentGuid = payments.associate { it.id to it.guid }
        val receiptDocGuid = receiptDocs.associate { it.id to it.guid }; val receiptItemGuid = receiptItems.associate { it.id to it.guid }
        val menuGuid = menus.associate { it.id to it.guid }; val expenseDocGuid = expenseDocs.associate { it.id to it.guid }
        val rootEntries = roots(section, selected, products, payments, receiptDocs, menus, expenseDocs)
        return JSONObject().apply {
            put("format", 1); put("section", section.route)
            put("roots", rootEntries.toJson { JSONObject().put("guid", it.guid).put("name", it.name) })
            put("products", chosenProducts.toJson { JSONObject().put("guid", it.guid).put("name", it.name).put("unit", it.unit) })
            put("payments", chosenPayments.toJson { JSONObject().put("guid", it.guid).put("name", it.name) })
            put("receiptDocuments", chosenReceiptDocs.toJson { JSONObject().put("guid", it.guid).put("date", it.date) })
            put("receiptItems", chosenReceiptItems.toJson { JSONObject().put("guid", it.guid).put("documentGuid", receiptDocGuid.getValue(it.documentId)).put("productGuid", productGuid.getValue(it.productId)).put("quantity", it.quantity).put("unitPriceMinor", it.unitPriceMinor).put("totalCostMinor", it.totalCostMinor) })
            put("menuPositions", chosenMenus.toJson { JSONObject().put("guid", it.guid).put("name", it.name).put("price", it.price) })
            put("menuIngredients", chosenIngredients.toJson { JSONObject().put("guid", it.guid).put("menuGuid", menuGuid.getValue(it.menuPositionId)).put("productGuid", productGuid.getValue(it.productId)).put("quantity", it.quantity).put("choiceGroup", it.choiceGroup) })
            put("expenseDocuments", chosenExpenseDocs.toJson { JSONObject().put("guid", it.guid).put("date", it.date) })
            put("expenseItems", chosenExpenseItems.toJson { item ->
                val choices = JSONObject()
                decodeChoices(item.ingredientChoices).forEach { (group, productId) -> choices.put(group, productGuid[productId]) }
                JSONObject().put("guid", item.guid).put("documentGuid", expenseDocGuid.getValue(item.documentId)).put("menuGuid", menuGuid.getValue(item.menuPositionId)).put("paymentGuid", paymentGuid.getValue(item.paymentMethodId)).put("quantity", item.quantity).put("comment", item.comment).put("ingredientChoices", choices)
            })
            put("allocations", chosenAllocations.toJson { JSONObject().put("guid", it.guid).put("expenseGuid", expenseItems.first { e -> e.id == it.expenseItemId }.guid).put("receiptItemGuid", receiptItemGuid.getValue(it.receiptItemId)).put("productGuid", productGuid.getValue(it.productId)).put("quantity", it.quantity) })
        }.toString(2)
    }

    fun parse(json: String, expected: TransferSection): TransferFile {
        val root = JSONObject(json)
        require(root.optInt("format") == 1) { "Неподдерживаемый формат файла" }
        val section = TransferSection.fromRoute(root.getString("section"))
        require(section == expected) { "Файл предназначен для раздела «${section.route}»" }
        val entries = root.array("roots").objects().map { TransferEntry(it.getString("guid"), it.getString("name")) }
        return TransferFile(section, entries, json)
    }

    suspend fun import(file: TransferFile, selected: Set<String>) = database.withTransaction {
        val root = JSONObject(file.json)
        val products = root.array("products").objects(); val payments = root.array("payments").objects()
        val receiptDocs = root.array("receiptDocuments").objects(); val receiptItems = root.array("receiptItems").objects()
        val menus = root.array("menuPositions").objects(); val ingredients = root.array("menuIngredients").objects()
        val expenseDocs = root.array("expenseDocuments").objects(); val expenseItems = root.array("expenseItems").objects()
        val allocations = root.array("allocations").objects()
        val allowed = dependencyGuids(file.section, selected, receiptDocs, receiptItems, menus, ingredients, expenseDocs, expenseItems, allocations)

        val productIds = mutableMapOf<String, Long>()
        products.filter { it.str("guid") in allowed }.forEach { o ->
            val guid = o.str("guid"); val old = dao.productByGuid(guid)
            val value = Product(old?.id ?: 0, o.str("name"), o.str("unit"), guid); dao.saveProduct(value)
            productIds[guid] = dao.productByGuid(guid)!!.id
        }
        val paymentIds = mutableMapOf<String, Long>()
        payments.filter { it.str("guid") in allowed }.forEach { o ->
            val guid = o.str("guid"); val old = dao.paymentByGuid(guid)
            dao.savePaymentMethod(PaymentMethod(old?.id ?: 0, o.str("name"), guid)); paymentIds[guid] = dao.paymentByGuid(guid)!!.id
        }
        val receiptDocIds = mutableMapOf<String, Long>()
        receiptDocs.filter { it.str("guid") in allowed }.forEach { o ->
            val guid = o.str("guid"); val old = dao.receiptDocumentByGuid(guid)
            dao.saveReceiptDocument(ReceiptDocument(old?.id ?: 0, o.str("date"), guid)); receiptDocIds[guid] = dao.receiptDocumentByGuid(guid)!!.id
        }
        val receiptItemIds = mutableMapOf<String, Long>()
        receiptItems.filter { it.str("guid") in allowed }.forEach { o ->
            val guid = o.str("guid"); val old = dao.receiptItemByGuid(guid)
            dao.saveReceiptItem(ReceiptItem(old?.id ?: 0, receiptDocIds.getValue(o.str("documentGuid")), productIds.getValue(o.str("productGuid")), o.str("quantity"), o.getLong("unitPriceMinor"), o.getLong("totalCostMinor"), guid))
            receiptItemIds[guid] = dao.receiptItemByGuid(guid)!!.id
        }
        val menuIds = mutableMapOf<String, Long>()
        menus.filter { it.str("guid") in allowed }.forEach { o ->
            val guid = o.str("guid"); val old = dao.menuPositionByGuid(guid)
            dao.saveMenuPosition(MenuPosition(old?.id ?: 0, o.str("name"), o.optLong("price"), guid)); menuIds[guid] = dao.menuPositionByGuid(guid)!!.id
        }
        ingredients.filter { it.str("guid") in allowed }.forEach { o ->
            val guid = o.str("guid")
            dao.saveMenuIngredient(MenuIngredient(
                menuPositionId = menuIds.getValue(o.str("menuGuid")),
                productId = productIds.getValue(o.str("productGuid")),
                quantity = o.str("quantity"),
                choiceGroup = o.optString("choiceGroup").ifBlank { guid },
                guid = guid,
            ))
        }
        val expenseDocIds = mutableMapOf<String, Long>()
        expenseDocs.filter { it.str("guid") in allowed }.forEach { o ->
            val guid = o.str("guid"); val old = dao.expenseDocumentByGuid(guid)
            dao.saveExpenseDocument(ExpenseDocument(old?.id ?: 0, o.str("date"), guid)); expenseDocIds[guid] = dao.expenseDocumentByGuid(guid)!!.id
        }
        val expenseItemIds = mutableMapOf<String, Long>()
        expenseItems.filter { it.str("guid") in allowed }.forEach { o ->
            val guid = o.str("guid"); val old = dao.expenseItemByGuid(guid)
            val importedChoices = o.optJSONObject("ingredientChoices")?.let { choices ->
                choices.keys().asSequence().mapNotNull { group ->
                    productIds[choices.optString(group)]?.let { group to it }
                }.toMap()
            }.orEmpty()
            dao.saveExpenseItem(ExpenseItem(
                id = old?.id ?: 0,
                documentId = expenseDocIds.getValue(o.str("documentGuid")),
                menuPositionId = menuIds.getValue(o.str("menuGuid")),
                quantity = o.str("quantity"),
                paymentMethodId = paymentIds.getValue(o.str("paymentGuid")),
                comment = o.optString("comment"),
                ingredientChoices = encodeChoices(importedChoices),
                guid = guid,
            ))
            expenseItemIds[guid] = dao.expenseItemByGuid(guid)!!.id
        }
        expenseItemIds.values.forEach { dao.deleteAllocations(it) }
        allocations.filter { it.str("guid") in allowed }.forEach { o ->
            val guid = o.str("guid"); val old = dao.expenseAllocationByGuid(guid)
            dao.saveExpenseAllocation(ExpenseAllocation(old?.id ?: 0, expenseItemIds.getValue(o.str("expenseGuid")), receiptItemIds.getValue(o.str("receiptItemGuid")), productIds.getValue(o.str("productGuid")), o.str("quantity"), guid))
        }
    }

    private fun dependencyGuids(section: TransferSection, selected: Set<String>, receiptDocs: List<JSONObject>, receiptItems: List<JSONObject>, menus: List<JSONObject>, ingredients: List<JSONObject>, expenseDocs: List<JSONObject>, expenseItems: List<JSONObject>, allocations: List<JSONObject>): Set<String> {
        val result = selected.toMutableSet()
        fun add(objects: List<JSONObject>, predicate: (JSONObject) -> Boolean) { objects.filter(predicate).forEach { o -> result += o.str("guid"); o.keys().forEach { key -> if (key.endsWith("Guid")) result += o.str(key) } } }
        if (section == TransferSection.RECEIPTS) add(receiptItems) { it.str("documentGuid") in selected }
        if (section == TransferSection.MENU) add(ingredients) { it.str("menuGuid") in selected }
        if (section == TransferSection.EXPENSES) {
            add(expenseItems) { it.str("documentGuid") in selected }; val expenseGuids = result.toSet()
            add(allocations) { it.str("expenseGuid") in expenseGuids }
            val receiptItemGuids = result.toSet(); add(receiptItems) { it.str("guid") in receiptItemGuids }
            val receiptDocGuids = result.toSet(); add(receiptDocs) { it.str("guid") in receiptDocGuids }
            val menuGuids = result.toSet(); add(menus) { it.str("guid") in menuGuids }
            val expandedMenus = result.toSet(); add(ingredients) { it.str("menuGuid") in expandedMenus }
        }
        return result
    }

    private fun roots(section: TransferSection, selected: Set<String>, products: List<Product>, payments: List<PaymentMethod>, receipts: List<ReceiptDocument>, menus: List<MenuPosition>, expenses: List<ExpenseDocument>) = when (section) {
        TransferSection.PRODUCTS -> products.filter { it.guid in selected }.map { TransferEntry(it.guid, it.name) }
        TransferSection.PAYMENTS -> payments.filter { it.guid in selected }.map { TransferEntry(it.guid, it.name) }
        TransferSection.RECEIPTS -> receipts.filter { it.guid in selected }.map { TransferEntry(it.guid, it.date) }
        TransferSection.MENU -> menus.filter { it.guid in selected }.map { TransferEntry(it.guid, it.name) }
        TransferSection.EXPENSES -> expenses.filter { it.guid in selected }.map { TransferEntry(it.guid, it.date) }
    }
}

private fun <T> List<T>.toJson(block: (T) -> JSONObject) = JSONArray().also { array -> forEach { array.put(block(it)) } }
private fun JSONObject.array(name: String) = optJSONArray(name) ?: JSONArray()
private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONObject.str(name: String) = getString(name)
private fun encodeChoices(choices: Map<String, Long>) = choices.entries.joinToString(",") { "${it.key}:${it.value}" }
private fun decodeChoices(value: String): Map<String, Long> = value.split(',').mapNotNull { pair ->
    val parts = pair.split(':', limit = 2)
    if (parts.size == 2) parts[1].toLongOrNull()?.let { parts[0] to it } else null
}.toMap()
