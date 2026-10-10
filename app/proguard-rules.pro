# Reglas de R8 para MiDinero.
# Room, WorkManager, Glance y Compose incluyen sus propias reglas (consumer rules).

# Los enums se guardan por nombre en la base de datos y en las copias de seguridad.
-keepclassmembers enum com.midinero.control.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Servicios y receptores declarados en el manifiesto (por si acaso; AAPT ya genera reglas para ellos).
-keep class com.midinero.control.service.** extends android.app.Service { <init>(); }
-keep class com.midinero.control.service.** extends android.content.BroadcastReceiver { <init>(); }
-keep class com.midinero.control.service.RecurringWorker { <init>(...); }

# Mensajes de error legibles en los informes de fallos.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keep class com.midinero.control.watch.WatchSyncWorker { <init>(...); }
