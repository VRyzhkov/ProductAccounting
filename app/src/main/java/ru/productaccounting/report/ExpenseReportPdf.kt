package ru.productaccounting.report

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.math.BigDecimal

data class ExpenseReportLine(
    val position: String,
    val quantity: String,
    val price: Long,
    val payment: String,
    val comment: String,
)

object ExpenseReportPdf {
    fun write(date: String, lines: List<ExpenseReportLine>, output: OutputStream) {
        val pdf = PdfDocument()
        val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(35, 35, 35); textSize = 10f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val bold = Paint(regular).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val muted = Paint(regular).apply { color = Color.rgb(95, 95, 95); textSize = 9f }
        val totals = lines.groupBy { it.payment }.mapValues { (_, values) -> values.sumOf { it.total() } }.toSortedMap()
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun startPage() {
            page?.let {
                it.canvas.drawText("Страница $pageNumber", 490f, 818f, muted)
                pdf.finishPage(it)
            }
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            val canvas = page!!.canvas
            canvas.drawText("Отчёт по расходу", 40f, 48f, Paint(bold).apply { textSize = 18f })
            canvas.drawText("Дата: $date", 40f, 70f, regular)
            canvas.drawLine(40f, 82f, 555f, 82f, muted)
            y = 104f
            canvas.drawText("№", 40f, y, bold)
            canvas.drawText("Позиция", 65f, y, bold)
            canvas.drawText("Кол.", 300f, y, bold)
            canvas.drawText("Цена", 345f, y, bold)
            canvas.drawText("Сумма", 405f, y, bold)
            canvas.drawText("Оплата", 475f, y, bold)
            y += 12f
            canvas.drawLine(40f, y, 555f, y, muted)
            y += 18f
        }

        fun ensure(height: Float) { if (page == null || y + height > 790f) startPage() }
        startPage()
        lines.forEachIndexed { index, line ->
            val height = if (line.comment.isBlank()) 24f else 42f
            ensure(height)
            val canvas = page!!.canvas
            canvas.drawText((index + 1).toString(), 40f, y, regular)
            canvas.drawText(line.position.fit(31), 65f, y, regular)
            canvas.drawText(line.quantity, 300f, y, regular)
            canvas.drawText("${line.price} ₽", 345f, y, regular)
            canvas.drawText("${line.total().plain()} ₽", 405f, y, regular)
            canvas.drawText(line.payment.fit(13), 475f, y, regular)
            y += 10f
            if (line.comment.isNotBlank()) {
                canvas.drawText("Комментарий: ${line.comment.fit(86)}", 65f, y + 7f, muted)
                y += 18f
            }
            canvas.drawLine(40f, y + 5f, 555f, y + 5f, Paint(muted).apply { color = Color.rgb(220, 220, 220) })
            y += 18f
        }
        ensure(42f + totals.size * 20f)
        val canvas = page!!.canvas
        canvas.drawText("Итого по способам оплаты", 40f, y, Paint(bold).apply { textSize = 13f })
        y += 24f
        totals.forEach { (payment, total) ->
            canvas.drawText(payment, 65f, y, regular)
            canvas.drawText("${total.plain()} ₽", 405f, y, bold)
            y += 20f
        }
        canvas.drawText("Всего", 65f, y + 4f, bold)
        canvas.drawText("${totals.values.fold(BigDecimal.ZERO, BigDecimal::add).plain()} ₽", 405f, y + 4f, bold)
        page?.let {
            it.canvas.drawText("Страница $pageNumber", 490f, 818f, muted)
            pdf.finishPage(it)
        }
        pdf.writeTo(output)
        pdf.close()
    }

    private fun ExpenseReportLine.total() = quantity.replace(',', '.').toBigDecimal() * price.toBigDecimal()
    private fun BigDecimal.plain() = stripTrailingZeros().toPlainString()
    private fun String.fit(max: Int) = if (length <= max) this else take(max - 1) + "…"
}
