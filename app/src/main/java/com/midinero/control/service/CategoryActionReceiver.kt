package com.midinero.control.service

import com.midinero.control.domain.tr
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.midinero.control.MiDineroApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Recibe el botón de categoría de la notificación de pago y la asigna sin abrir la app. */
class CategoryActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ASSIGN) return
        val expenseId = intent.getLongExtra(EXTRA_EXPENSE_ID, -1)
        val categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1)
        if (expenseId < 0 || categoryId < 0) return
        val app = context.applicationContext as MiDineroApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                val ok = app.repository.assignCategory(expenseId, categoryId)
                AppNotifications.cancelPayment(app, expenseId)
                if (ok) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, tr("Categoría guardada", "Category saved"), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MiDinero", "No se pudo asignar la categoría", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ASSIGN = "com.midinero.control.ASSIGN_CATEGORY"
        const val EXTRA_EXPENSE_ID = "expense_id"
        const val EXTRA_CATEGORY_ID = "category_id"
    }
}
