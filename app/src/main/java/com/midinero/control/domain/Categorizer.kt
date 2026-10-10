package com.midinero.control.domain

import com.midinero.control.data.DefaultCategories as C
import java.text.Normalizer

/**
 * Categorización automática por palabras clave del nombre del comercio.
 * Las reglas aprendidas (cuando el usuario corrige una categoría) tienen prioridad
 * y se aplican antes, en el repositorio.
 */
object Categorizer {

    /** Palabras clave por categoría. Si coinciden varias, gana la más larga (ver [suggestCategoryName]). */
    private val keywords: List<Pair<String, List<String>>> = listOf(
        C.COMIDA_DOMICILIO to listOf(
            "glovo", "just eat", "justeat", "uber eats", "ubereats", "deliveroo", "telepizza",
            "domino s", "dominos", "pizza hut", "papa john", "too good to go", "burger king delivery",
        ),
        C.SUPERMERCADO to listOf(
            "mercadona", "carrefour", "lidl", "aldi", "dia", "eroski", "alcampo", "hipercor",
            "consum", "bonpreu", "caprabo", "supercor", "ahorramas", "gadis", "froiz", "coviran",
            "spar", "condis", "masymas", "simply", "supermercado", "super", "hiper",
            "mercado", "fruteria", "carniceria", "panaderia",
        ),
        C.GASOLINA to listOf(
            "repsol", "cepsa", "moeve", "bp", "galp", "shell", "petronor", "ballenoil", "plenoil",
            "petroprix", "gasolinera", "estacion de servicio", "avia", "disa", "esclatoil",
            "carburantes", "combustible", "gasoleo",
        ),
        C.SUSCRIPCIONES to listOf(
            "netflix", "spotify", "hbo", "disney", "prime video", "amazon prime", "apple com",
            "icloud", "google one", "youtube", "dazn", "filmin", "movistar plus", "audible",
            "chatgpt", "openai", "claude", "anthropic", "patreon", "apple music", "apple tv", "tidal",
            "deezer", "twitch", "xbox game pass", "playstation plus", "nintendo switch online",
        ),
        C.TRANSPORTE to listOf(
            "renfe", "metro", "emt", "tmb", "cabify", "uber", "bolt", "free now", "freenow",
            "taxi", "parking", "aparcamiento", "autopista", "peaje", "alsa", "blablacar", "iryo",
            "ouigo", "avanza", "bicing", "tussam", "bilbobus", "vueling", "iberia", "ryanair",
            "air europa", "easyjet", "volotea", "itv", "taller", "neumaticos", "norauto", "feu vert",
            "lime", "dott", "cooltra", "acciona", "zity", "share now", "via t", "telepeaje",
        ),
        C.RESTAURANTES to listOf(
            "restaurante", "rest", "bar", "cafeteria", "cafe", "burger", "mcdonald", "mcdonalds",
            "kfc", "taberna", "tapas", "pizzeria", "sushi", "vips", "foster", "goiko",
            "montaditos", "starbucks", "tagliatella", "ginos", "udon", "wok", "asador", "meson",
            "cerveceria", "brasa", "kebab", "grill", "tasca", "bodega", "churreria", "heladeria",
        ),
        C.OCIO to listOf(
            "cine", "cinesa", "yelmo", "kinepolis", "ocine", "teatro", "ticketmaster", "entradas",
            "museo", "bolera", "steam", "playstation", "nintendo", "xbox", "concierto", "discoteca",
            "pub", "karaoke", "escape", "parque", "gimnasio", "gym", "basic fit", "fnac",
        ),
        C.HOGAR to listOf(
            "ikea", "leroy merlin", "bricomart", "bricodepot", "brico", "aki", "endesa",
            "iberdrola", "naturgy", "ferreteria", "jysk", "maisons du monde", "alquiler", "cochera",
            "garaje", "plaza de garaje", "comunidad", "hipoteca", "agua", "luz", "gas", "internet",
            "fibra", "seguro hogar", "movistar", "vodafone", "orange", "digi", "yoigo", "pepephone",
            "masmovil", "lowi", "o2", "simyo", "holaluz", "totalenergies", "canal de isabel",
            "aguas de", "lavanderia", "tintoreria",
        ),
        C.SALUD to listOf(
            "farmacia", "parafarmacia", "clinica", "dentista", "dental", "optica", "hospital",
            "sanitas", "adeslas", "fisioterapia", "veterinario", "veterinaria", "podologo", "psicologo",
            "laboratorio", "analisis clinicos", "dkv", "asisa", "mapfre salud",
        ),
        C.COMPRAS to listOf(
            "amazon", "amzn", "zara", "primark", "mango", "el corte ingles", "decathlon",
            "mediamarkt", "media markt", "aliexpress", "shein", "pull bear", "bershka", "h m",
            "stradivarius", "massimo dutti", "lefties", "druni", "sephora", "primor", "temu",
            "apple store", "pccomponentes", "worten", "tiger",
        ),
    )

    private val compiled: List<Triple<String, String, Regex>> = keywords.flatMap { (cat, words) ->
        words.map { Triple(cat, normalize(it), Regex("""\b${Regex.escape(normalize(it))}\b""")) }
    }

    /**
     * Devuelve el nombre de la categoría sugerida, o null si no la reconoce.
     * Si varias palabras clave coinciden gana la más específica (la más larga):
     * "Farmacia Dia" es Salud y no Supermercado; "Uber Eats" es comida y no transporte.
     * A igual longitud, gana la marca conocida ([MerchantCatalog]) y después la categoría que aparece antes en la lista.
     */
    fun suggestCategoryName(merchant: String): String? {
        val key = normalize(merchant)
        if (key.isBlank()) return null
        // Las marcas conocidas compiten con las palabras clave: gana la coincidencia más larga.
        val brand = MerchantCatalog.bestHit(key)
        var best: String? = brand?.merchant?.category
        var bestLength = brand?.alias?.length ?: 0
        for ((cat, word, pattern) in compiled) {
            if (word.length > bestLength && pattern.containsMatchIn(key)) {
                best = cat
                bestLength = word.length
            }
        }
        return best
    }

    /** Clave estable para un comercio: minúsculas, sin acentos, sin números ni símbolos. */
    fun merchantKey(merchant: String): String =
        normalize(merchant).replace(Regex("""\b\d+\b"""), " ").replace(Regex("""\s+"""), " ").trim()

    fun normalize(text: String): String {
        val noAccents = Normalizer.normalize(text.lowercase(SPANISH), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
        return noAccents.replace(Regex("""[^a-z0-9]+"""), " ").trim()
    }
}
