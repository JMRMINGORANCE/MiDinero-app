package com.midinero.control.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
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
import com.midinero.control.link.WatchState

/**
 * Tarjeta «Hoy puedes gastar»: el anillo del presupuesto del mes, la cifra de hoy en lima y un botón
 * para apuntar un gasto. Se redibuja cada vez que llega un resumen del móvil.
 */
class SpendTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val state = WatchStore.current(this)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES)
            .setFreshnessIntervalMillis(60 * 60_000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(state)))
            .build()
        return CallbackToFutureAdapter.getFuture { it.set(tile); "tile" }
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder().setVersion(RESOURCES).build()
        return CallbackToFutureAdapter.getFuture { it.set(resources); "resources" }
    }

    private fun open(extra: String?): ModifiersBuilders.Clickable {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(MainActivity::class.java.name)
        if (extra != null) {
            activity.addKeyToExtraMapping(MainActivity.EXTRA_OPEN, ActionBuilders.AndroidStringExtra.Builder().setValue(extra).build())
        }
        return ModifiersBuilders.Clickable.Builder()
            .setId(extra ?: "open")
            .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build())
            .build()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(1)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build(),
            )
            .build()

    private fun spacer(height: Float) = LayoutElementBuilders.Spacer.Builder().setHeight(dp(height)).build()

    private fun layout(state: WatchState): LayoutElementBuilders.LayoutElement {
        val ratio = state.budgetRatio
        val ringColor = when {
            ratio > 1f -> CORAL
            ratio >= 0 && ratio > state.pace + 0.1f -> AMBER
            else -> LIME
        }
        val done = if (ratio < 0) 0f else SWEEP * ratio.coerceIn(0f, 1f)
        // Anillo con el hueco arriba (como en la app): lo gastado en color y el resto en gris
        val arc = LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(degrees(GAP / 2))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
        if (done > 0.5f) {
            arc.addContent(LayoutElementBuilders.ArcLine.Builder().setLength(degrees(done)).setThickness(dp(8f)).setColor(argb(ringColor)).build())
        }
        if (SWEEP - done > 0.5f) {
            arc.addContent(LayoutElementBuilders.ArcLine.Builder().setLength(degrees(SWEEP - done)).setThickness(dp(8f)).setColor(argb(TRACK)).build())
        }

        val center = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        if (state.isEmpty) {
            center.addContent(text("MiDinero", 18f, LIME, bold = true))
                .addContent(spacer(4f))
                .addContent(text(tr("Abre la app en el móvil", "Open the phone app"), 12f, SOFT))
        } else {
            center.addContent(text(state.monthLabel.uppercase(), 10f, FAINT, bold = true))
                .addContent(spacer(2f))
                .addContent(text(state.todayLabel, 12f, SOFT))
                .addContent(text(state.todayValue, if (state.todayValue.length > 9) 24f else 30f, if (state.todayOver) CORAL else LIME, bold = true))
                .addContent(spacer(4f))
                .addContent(
                    text(
                        if (state.budget.isNotBlank()) "${state.spent} / ${state.budget}" else state.spent,
                        12f, WHITE,
                    ),
                )
        }

        val add = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(44f)).setHeight(dp(44f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(open("add"))
                    .setBackground(
                        ModifiersBuilders.Background.Builder().setColor(argb(LIME))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(22f)).build()).build(),
                    )
                    .build(),
            )
            .addContent(text("+", 26f, INK, bold = true))
            .build()

        val bottom = LayoutElementBuilders.Box.Builder()
            .setWidth(expand()).setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(ModifiersBuilders.Padding.Builder().setBottom(dp(10f)).build())
                    .build(),
            )
            .addContent(add)
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand()).setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open(null)).build())
            .addContent(arc.build())
            .addContent(center.build())
            .apply { if (!state.isEmpty) addContent(bottom) }
            .build()
    }

    private companion object {
        const val RESOURCES = "1"
        const val GAP = 64f
        const val SWEEP = 360f - GAP
        const val LIME = 0xFFD4F54A.toInt()
        const val CORAL = 0xFFFF7A7F.toInt()
        const val AMBER = 0xFFFFC857.toInt()
        const val TRACK = 0x33FFFFFF
        const val WHITE = 0xFFFFFFFF.toInt()
        const val SOFT = 0xA3FFFFFF.toInt()
        const val FAINT = 0x5CFFFFFF
        const val INK = 0xFF141318.toInt()
    }
}
