package com.midinero.control.domain

/** App cuyas notificaciones de pago puede leer MiDinero. */
data class PaymentApp(val packageName: String, val name: String, val enabledByDefault: Boolean)

object PaymentApps {
    val all: List<PaymentApp> get() = listOf(
        PaymentApp("com.google.android.apps.walletnfcrel", "Google Wallet / Google Pay", true),
        // Play Services publica algunas notificaciones de pago contactless.
        PaymentApp("com.google.android.gms", tr("Servicios de Google (pagos contactless)", "Google services (contactless payments)"), true),
        PaymentApp("com.google.android.apps.nbu.paisa.user", "Google Pay (India)", true),
        PaymentApp("com.samsung.android.spay", "Samsung Wallet", false),
        PaymentApp("com.revolut.revolut", "Revolut", false),
        PaymentApp("de.number26.android", "N26", false),
        PaymentApp("com.bbva.bbvacontigo", "BBVA", false),
        PaymentApp("es.lacaixa.mobile.android.newwapicon", "CaixaBank", false),
        PaymentApp("es.bancosantander.apps", "Santander", false),
        PaymentApp("www.ingdirect.nativeframe", "ING", false),
        PaymentApp("es.openbank.mobile", "Openbank", false),
        PaymentApp("net.inverline.bancosabadell.officelocator.android", "Banco Sabadell", false),
        PaymentApp("com.bankinter.launcher", "Bankinter", false),
        PaymentApp("com.paypal.android.p2pmobile", "PayPal", false),
        PaymentApp("com.transferwise.android", "Wise", false),
        PaymentApp("com.imaginecurve.curve.prd", "Curve", false),
        // Tarjetas cripto
        PaymentApp("com.bleap", "Bleap", false),
        PaymentApp("com.bybit.app", "Bybit", false),
        PaymentApp("com.bybit.eu", "Bybit EU", false),
    )

    val defaultEnabled: Set<String> = all.filter { it.enabledByDefault }.map { it.packageName }.toSet()

    fun nameOf(packageName: String): String = all.firstOrNull { it.packageName == packageName }?.name ?: packageName

    /** ¿Parece un aviso de dinero? (para guardar los que el parser no entiende y poder revisarlos) */
    fun looksLikeMoney(text: String): Boolean = Regex("""\d\s?(€|EUR)|(€|EUR)\s?\d""", RegexOption.IGNORE_CASE).containsMatchIn(text)
}
