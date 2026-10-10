package com.midinero.control.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.midinero.control.R
import com.midinero.control.domain.formatCents
import com.midinero.control.domain.tr
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/** Lo que sale en la imagen del resumen del mes. */
data class MonthReportImage(
    val monthLabel: String,
    val totalCents: Long,
    val previousTotalCents: Long,
    val previousMonthName: String,
    val incomeCents: Long,
    val dailyAverageCents: Long,
    /** Gasto acumulado de cada día del mes (hasta hoy en el mes en curso). */
    val cumulative: List<Long>,
    /** Categorías de más a menos gasto: nombre con emoji, color ARGB e importe. */
    val categories: List<Triple<String, Int, Long>>,
)

/** Lo que sale en la imagen del resumen del año. */
data class YearReportImage(
    val year: Int,
    val totalCents: Long,
    val previousCents: Long,
    /** Año en curso: la comparación es con el año pasado a estas alturas. */
    val partial: Boolean,
    val incomeCents: Long,
    val monthlyAverageCents: Long,
    /** Gasto de enero a diciembre. */
    val byMonth: List<Long>,
    val maxMonthName: String,
    val topMerchant: Pair<String, Long>?,
    val categories: List<Triple<String, Int, Long>>,
)

/** Dibuja el resumen del mes como una imagen para compartir, con el estilo de la app (tinta y lima). */
object ReportImage {
    private const val W = 1080
    private const val H = 1350
    private const val INK = 0xFF141318.toInt()
    private const val LIME = 0xFFD4F54A.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val SOFT = 0x9EFFFFFF.toInt()
    private const val RED = 0xFFE5484D.toInt()

    private fun font(context: Context, id: Int): Typeface =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.DEFAULT_BOLD

    fun render(context: Context, r: MonthReportImage): Bitmap {
        val bitmap = androidx.core.graphics.createBitmap(W, H)
        val c = Canvas(bitmap)
        val bold = font(context, R.font.jakarta_extrabold)
        val semi = font(context, R.font.jakarta_semibold)
        val regular = font(context, R.font.jakarta_regular)
        fun text(size: Float, color: Int, face: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = face
        }

        // Fondo de tinta con resplandor lima
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), 0xFF23222A.toInt(), INK, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        val glow = Paint().apply { shader = RadialGradient(W * 0.95f, 0f, W * 0.8f, 0x47D4F54A, 0x00D4F54A, Shader.TileMode.CLAMP) }
        c.drawCircle(W * 0.95f, 0f, W * 0.8f, glow)

        val left = 80f
        // Cabecera
        c.drawText("MiDinero", left, 130f, text(40f, LIME, bold))
        c.drawText(r.monthLabel, left, 190f, text(44f, SOFT, semi))
        c.drawText(tr("Gastado", "Spent"), left, 300f, text(40f, SOFT, semi))
        c.drawText(formatCents(r.totalCents), left - 4f, 420f, text(128f, WHITE, bold))

        // Comparación con el mes anterior en una píldora
        var y = 500f
        if (r.previousTotalCents > 0) {
            val diff = r.totalCents - r.previousTotalCents
            val pct = (abs(diff) * 100 / r.previousTotalCents).toInt()
            val up = diff > 0
            val label = (if (up) "▲ " else "▼ ") + tr("$pct % vs ${r.previousMonthName}", "$pct % vs ${r.previousMonthName}")
            val p = text(38f, if (up) WHITE else INK, bold)
            val w = p.measureText(label)
            c.drawRoundRect(RectF(left, y - 48f, left + w + 56f, y + 20f), 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (up) RED else LIME })
            c.drawText(label, left + 28f, y, p)
        }

        // Curva del gasto acumulado
        y = 560f
        drawCurve(c, RectF(left, y, W - left, y + 250f), r.cumulative, LIME, strokeWidth = 8f)

        // Ingresos, ahorro y media diaria
        y = 900f
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14FFFFFF }
        c.drawRoundRect(RectF(left, y, W - left, y + 150f), 36f, 36f, boxPaint)
        val cols = listOf(
            tr("Ingresos", "Income") to if (r.incomeCents > 0) "+" + formatCents(r.incomeCents) else "—",
            tr("Ahorro", "Saved") to if (r.incomeCents > 0) formatCents(r.incomeCents - r.totalCents) else "—",
            tr("Media al día", "Daily average") to formatCents(r.dailyAverageCents),
        )
        val colW = (W - 2 * left) / 3
        cols.forEachIndexed { i, (label, value) ->
            val x = left + 36f + i * colW
            c.drawText(label, x, y + 58f, text(30f, SOFT, semi))
            val vp = text(40f, WHITE, bold)
            while (vp.measureText(value) > colW - 44f && vp.textSize > 22f) vp.textSize -= 2f
            c.drawText(value, x, y + 112f, vp)
        }

        // Categorías principales con su barra
        y = 1110f
        c.drawText(tr("En qué se ha ido", "Where it went"), left, y, text(36f, SOFT, semi))
        val top = r.categories.take(3)
        val max = top.maxOfOrNull { it.third }?.coerceAtLeast(1) ?: 1
        top.forEachIndexed { i, (name, color, cents) ->
            val rowY = y + 60f + i * 62f
            c.drawText(name, left, rowY, text(34f, WHITE, semi))
            val amount = formatCents(cents)
            val ap = text(34f, WHITE, bold)
            c.drawText(amount, W - left - ap.measureText(amount), rowY, ap)
            val barLeft = left + 380f
            val barRight = W - left - 230f
            c.drawRoundRect(RectF(barLeft, rowY - 22f, barRight, rowY - 6f), 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x1FFFFFFF })
            c.drawRoundRect(RectF(barLeft, rowY - 22f, barLeft + (barRight - barLeft) * cents / max, rowY - 6f), 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        }
        c.drawText(tr("Hecho con MiDinero · tus datos solo en tu móvil", "Made with MiDinero · your data only on your phone"), left, H - 50f, text(26f, 0x66FFFFFF, regular))
        return bitmap
    }

    /** Resumen del año: total, comparación, barras de los 12 meses y en qué se fue. */
    fun renderYear(context: Context, r: YearReportImage): Bitmap {
        val bitmap = androidx.core.graphics.createBitmap(W, H)
        val c = Canvas(bitmap)
        val bold = font(context, R.font.jakarta_extrabold)
        val semi = font(context, R.font.jakarta_semibold)
        val regular = font(context, R.font.jakarta_regular)
        fun text(size: Float, color: Int, face: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = face
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), 0xFF23222A.toInt(), INK, Shader.TileMode.CLAMP) })
        c.drawCircle(W * 0.95f, 0f, W * 0.8f, Paint().apply { shader = RadialGradient(W * 0.95f, 0f, W * 0.8f, 0x47D4F54A, 0x00D4F54A, Shader.TileMode.CLAMP) })
        val left = 80f
        c.drawText("MiDinero", left, 130f, text(40f, LIME, bold))
        c.drawText(tr("Mi año ${r.year}", "My ${r.year}"), left, 190f, text(44f, SOFT, semi))
        c.drawText(tr("Gastado", "Spent"), left, 300f, text(40f, SOFT, semi))
        val totalPaint = text(128f, WHITE, bold)
        val totalText = formatCents(r.totalCents)
        while (totalPaint.measureText(totalText) > W - 2 * left && totalPaint.textSize > 70f) totalPaint.textSize -= 4f
        c.drawText(totalText, left - 4f, 420f, totalPaint)

        var y = 500f
        if (r.previousCents > 0) {
            val diff = r.totalCents - r.previousCents
            val pct = (abs(diff) * 100 / r.previousCents).toInt()
            val up = diff > 0
            val label = (if (up) "▲ " else "▼ ") + tr("$pct % vs ${r.year - 1}", "$pct % vs ${r.year - 1}") + if (r.partial) tr(" a estas alturas", " so far") else ""
            val p = text(36f, if (up) WHITE else INK, bold)
            val w = p.measureText(label)
            c.drawRoundRect(RectF(left, y - 48f, left + w + 56f, y + 20f), 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (up) RED else LIME })
            c.drawText(label, left + 28f, y, p)
        }

        // Barras de los 12 meses
        y = 580f
        val area = RectF(left, y, W - left, y + 240f)
        val max = r.byMonth.maxOrNull()?.coerceAtLeast(1) ?: 1
        val slot = area.width() / 12
        val initials = if (com.midinero.control.domain.Lang.english) "JFMAMJJASOND" else "EFMAMJJASOND"
        val maxIndex = r.byMonth.indexOf(r.byMonth.maxOrNull() ?: -1)
        r.byMonth.forEachIndexed { i, v ->
            val h = (area.height() - 40f) * v / max
            val x0 = area.left + i * slot + slot * 0.18f
            val x1 = area.left + (i + 1) * slot - slot * 0.18f
            c.drawRoundRect(RectF(x0, area.bottom - 40f - h.coerceAtLeast(6f), x1, area.bottom - 40f), 12f, 12f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (i == maxIndex) LIME else 0x33FFFFFF })
            val ip = text(28f, SOFT, semi)
            val ch = initials[i].toString()
            c.drawText(ch, (x0 + x1) / 2 - ip.measureText(ch) / 2, area.bottom, ip)
        }

        y = 900f
        c.drawRoundRect(RectF(left, y, W - left, y + 150f), 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14FFFFFF })
        val cols = listOf(
            tr("Media al mes", "Monthly avg") to formatCents(r.monthlyAverageCents),
            tr("Mes más caro", "Priciest month") to r.maxMonthName.replaceFirstChar { it.uppercase() }.ifBlank { "—" },
            tr("Ahorro", "Saved") to if (r.incomeCents > 0) formatCents(r.incomeCents - r.totalCents) else "—",
        )
        val colW = (W - 2 * left) / 3
        cols.forEachIndexed { i, (label, value) ->
            val x = left + 36f + i * colW
            c.drawText(label, x, y + 58f, text(30f, SOFT, semi))
            val vp = text(40f, WHITE, bold)
            while (vp.measureText(value) > colW - 44f && vp.textSize > 22f) vp.textSize -= 2f
            c.drawText(value, x, y + 112f, vp)
        }

        y = 1110f
        c.drawText(tr("En qué se ha ido", "Where it went"), left, y, text(36f, SOFT, semi))
        val top = r.categories.take(3)
        val maxCat = top.maxOfOrNull { it.third }?.coerceAtLeast(1) ?: 1
        top.forEachIndexed { i, (name, color, cents) ->
            val rowY = y + 60f + i * 62f
            c.drawText(name, left, rowY, text(34f, WHITE, semi))
            val amount = formatCents(cents)
            val ap = text(34f, WHITE, bold)
            c.drawText(amount, W - left - ap.measureText(amount), rowY, ap)
            val barLeft = left + 380f
            val barRight = W - left - 230f
            c.drawRoundRect(RectF(barLeft, rowY - 22f, barRight, rowY - 6f), 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x1FFFFFFF })
            c.drawRoundRect(RectF(barLeft, rowY - 22f, barLeft + (barRight - barLeft) * cents / maxCat, rowY - 6f), 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        }
        val foot = r.topMerchant?.let { (n, cents) -> tr("Donde más: $n (${formatCents(cents)})", "Top merchant: $n (${formatCents(cents)})") }
            ?: tr("Hecho con MiDinero", "Made with MiDinero")
        c.drawText(foot, left, H - 50f, text(26f, 0x99FFFFFF.toInt(), regular))
        return bitmap
    }

    /** Curva de gasto acumulado (también la usa el widget). */
    fun drawCurve(c: Canvas, area: RectF, cumulative: List<Long>, color: Int, strokeWidth: Float) {
        if (cumulative.isEmpty()) return
        val max = cumulative.maxOrNull()?.coerceAtLeast(1) ?: 1
        val steps = (cumulative.size - 1).coerceAtLeast(1)
        fun x(i: Int) = area.left + area.width() * i / steps
        fun y(v: Long) = area.bottom - area.height() * 0.92f * v / max
        val line = Path().apply {
            moveTo(x(0), y(cumulative[0]))
            for (i in 1 until cumulative.size) lineTo(x(i), y(cumulative[i]))
        }
        val fill = Path(line).apply {
            lineTo(x(cumulative.size - 1), area.bottom)
            lineTo(area.left, area.bottom)
            close()
        }
        val alphaColor = (color and 0x00FFFFFF) or 0x59000000
        c.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, area.top, 0f, area.bottom, alphaColor, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
        c.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
        val last = cumulative.size - 1
        c.drawCircle(x(last), y(cumulative[last]), strokeWidth * 1.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    /** Guarda la imagen y abre el menú de compartir del móvil. */
    fun share(context: Context, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "resumen.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, tr("Compartir resumen del mes", "Share month summary")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
