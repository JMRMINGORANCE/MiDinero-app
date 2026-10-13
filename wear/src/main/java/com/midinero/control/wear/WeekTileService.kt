package com.midinero.control.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.midinero.control.link.WatchCategory
import com.midinero.control.link.WatchState

/** Tarjeta «Semana y límites»: lo que te queda esta semana y las dos categorías más cerca de su límite. */
class WeekTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES)
            .setFreshnessIntervalMillis(60 * 60_000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(WatchStore.current(this))))
            .build()
        return CallbackToFutureAdapter.getFuture { it.set(tile); "tile" }
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder().setVersion(RESOURCES).build()
        return CallbackToFutureAdapter.getFuture { it.set(resources); "resources" }
    }

    private fun openApp(): ModifiersBuilders.Clickable = ModifiersBuilders.Clickable.Builder()
        .setId("open")
        .setOnClick(
            ActionBuilders.LaunchAction.Builder().setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder().setPackageName(packageName).setClassName(MainActivity::class.java.name).build(),
            ).build(),
        )
        .build()

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = LayoutElementBuilders.Text.Builder()
        .setText(value)
        .setMaxLines(1)
        .setFontStyle(
            LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(size)).setColor(argb(color))
                .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                .build(),
        )
        .build()

    private fun spacer(height: Float) = LayoutElementBuilders.Spacer.Builder().setHeight(dp(height)).build()

    /** Barra de progreso de [width] dp. */
    private fun bar(ratio: Float, color: Int, width: Float = 110f): LayoutElementBuilders.LayoutElement {
        val filled = (width * ratio.coerceIn(0f, 1f)).coerceAtLeast(3f)
        fun piece(w: Float, c: Int) = LayoutElementBuilders.Box.Builder().setWidth(dp(w)).setHeight(dp(5f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder().setBackground(
                    ModifiersBuilders.Background.Builder().setColor(argb(c))
                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(2.5f)).build()).build(),
                ).build(),
            ).build()
        return LayoutElementBuilders.Box.Builder().setWidth(dp(width)).setHeight(dp(5f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .addContent(piece(width, TRACK))
            .addContent(piece(filled, color))
            .build()
    }

    private fun categoryRow(c: WatchCategory, hidden: Boolean): LayoutElementBuilders.LayoutElement {
        val color = when {
            c.ratio > 1f -> CORAL
            c.ratio >= 0.8f -> AMBER
            else -> LIME
        }
        val pct = "${(c.ratio * 100).toInt()}%"
        return LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("${c.emoji} ${c.name} · ${if (hidden) pct else "${compactMoney(c.spent)}/${compactMoney(c.limit)}"}", 11f, WHITE))
            .addContent(spacer(3f))
            .addContent(bar(c.ratio, color))
            .build()
    }

    private fun layout(state: WatchState): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder().setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        if (state.isEmpty || state.weekValue.isBlank()) {
            column.addContent(text("MiDinero", 18f, LIME, bold = true))
                .addContent(spacer(4f))
                .addContent(text(tr("Abre la app en el móvil", "Open the phone app"), 12f, SOFT))
        } else {
            column.addContent(text(tr("SEMANA", "WEEK"), 10f, FAINT, bold = true))
                .addContent(text(state.weekLabel, 12f, SOFT))
                .addContent(text(state.weekValue, 28f, if (state.weekOver) CORAL else LIME, bold = true))
            if (state.weekRatio >= 0) column.addContent(spacer(4f)).addContent(bar(state.weekRatio, if (state.weekOver) CORAL else LIME, 90f))
            val risky = state.categories.filter { it.ratio >= 0 }.sortedByDescending { it.ratio }.take(2)
            risky.forEach { column.addContent(spacer(8f)).addContent(categoryRow(it, state.hidden)) }
        }
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand()).setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openApp()).build())
            .addContent(column.build())
            .build()
    }

    private companion object {
        const val RESOURCES = "1"
        const val LIME = 0xFFD4F54A.toInt()
        const val CORAL = 0xFFFF7A7F.toInt()
        const val AMBER = 0xFFFFC857.toInt()
        const val TRACK = 0x33FFFFFF
        const val WHITE = 0xFFFFFFFF.toInt()
        const val SOFT = 0xA3FFFFFF.toInt()
        const val FAINT = 0x5CFFFFFF
    }
}
