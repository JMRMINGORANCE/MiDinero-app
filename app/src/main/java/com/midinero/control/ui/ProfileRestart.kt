package com.midinero.control.ui

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process

/**
 * Reinicia la app para abrirla con otro perfil: cada perfil tiene su propia base de datos y sus ajustes,
 * y así todo (pantallas, widgets, reloj, avisos) pasa a usar los del perfil nuevo sin mezclar nada.
 * Lo hace una actividad en un proceso aparte, que cierra el de la app y la vuelve a abrir.
 */
class ProfileRestart : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pid = intent.getIntExtra(EXTRA_PID, -1)
        if (pid > 0) Process.killProcess(pid)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        private const val EXTRA_PID = "pid"

        fun restart(context: Context) {
            context.startActivity(
                Intent(context, ProfileRestart::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_PID, Process.myPid())
            )
        }

        fun isRestartProcess(context: Context): Boolean {
            val name = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName()
            else context.getSystemService(ActivityManager::class.java)?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }?.processName
            return name?.endsWith(":restart") == true
        }
    }
}
