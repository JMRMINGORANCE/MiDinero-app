package com.midinero.control.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import com.midinero.control.MiDineroApp
import com.midinero.control.domain.Lang
import com.midinero.control.service.AppNotifications
import com.midinero.control.widget.SpendingWidget
import kotlinx.coroutines.launch

/** Aplica y cambia el idioma de la app (ajuste propio y, en Android 13+, el idioma por app del sistema). */
object Language {
    /** Fija [Lang.english] según el ajuste y el idioma con el que se ha abierto la app. */
    fun apply(context: Context) {
        val app = context.applicationContext as MiDineroApp
        Lang.english = Lang.resolve(app.prefs.language, context.resources.configuration.locales[0].language)
    }

    /** Cambia el idioma y vuelve a abrir la pantalla para que se vea al momento. */
    fun change(activity: Activity, setting: String) {
        val app = activity.applicationContext as MiDineroApp
        app.prefs.language = setting
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android recrea la actividad con el nuevo idioma; también lo verás en Ajustes → Idioma de la app
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (setting == "system") LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(setting)
        }
        apply(activity)
        // Los nombres de los tipos de aviso se ven en los ajustes de notificaciones de Android
        AppNotifications.createChannels(app)
        app.appScope.launch { SpendingWidget.refresh(app) }
        // Desde Android 13 el propio sistema recrea la pantalla al cambiar applicationLocales
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) activity.recreate()
    }
}
