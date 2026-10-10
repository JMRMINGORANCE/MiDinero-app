package com.midinero.control.domain

import com.midinero.control.data.DefaultCategories as C
import java.util.concurrent.ConcurrentHashMap

/**
 * Un comercio conocido: nombre bonito, categoría por defecto y su distintivo (color de marca y siglas).
 * [aliases] son las formas en que suele aparecer en las notificaciones y extractos, ya normalizadas.
 */
data class KnownMerchant(
    val name: String,
    /** Nombre en español de la categoría por defecto (ver [C]). */
    val category: String,
    /** Color de marca, "#RRGGBB". */
    val color: String,
    /** Siglas del distintivo (1–3 caracteres). */
    val mark: String,
    val aliases: List<String>,
    /** Color del texto sobre [color]: blanco salvo en marcas de fondo claro. */
    val onColor: String = "#FFFFFF",
)

/**
 * Banco de comercios típicos en España, para reconocer los pagos desde el primer día:
 * les pone su categoría, un nombre limpio («MERCADONA VALENCIA 4432» → «Mercadona») y su distintivo.
 * Todo va dentro de la app; no se consulta nada fuera del móvil.
 */
object MerchantCatalog {

    /** Súbelo al añadir comercios: al arrancar se vuelven a clasificar los gastos sin categoría. */
    const val VERSION = 2

    private fun m(name: String, category: String, color: String, vararg aliases: String, mark: String? = null, onColor: String = "#FFFFFF") =
        KnownMerchant(
            name = name,
            category = category,
            color = color,
            mark = mark ?: defaultMark(name),
            aliases = (listOf(name) + aliases).map { Categorizer.normalize(it) }.filter { it.isNotBlank() }.distinct(),
            onColor = onColor,
        )

    private fun defaultMark(name: String): String {
        val words = name.split(' ', '-', '&', '.').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
        return if (words.size >= 2) words.take(2).joinToString("") { it.first().uppercase() } else name.take(1).uppercase()
    }

    private const val YELLOW_TEXT = "#1A1A1A"

    val all: List<KnownMerchant> = listOf(
        // Supermercados
        m("Mercadona", C.SUPERMERCADO, "#00843D"),
        m("Carrefour", C.SUPERMERCADO, "#004E9E", "carrefour express", "carrefour market", "centros comerciales carrefour"),
        m("Lidl", C.SUPERMERCADO, "#0050AA"),
        m("Aldi", C.SUPERMERCADO, "#00005F"),
        m("Dia", C.SUPERMERCADO, "#E30613", "dia %", "dia retail", "supermercados dia", "la plaza de dia"),
        m("Eroski", C.SUPERMERCADO, "#E2001A", "eroski city", "eroski center"),
        m("Alcampo", C.SUPERMERCADO, "#E30613", "auchan", "mi alcampo"),
        m("Hipercor", C.SUPERMERCADO, "#007A3D"),
        m("Supercor", C.SUPERMERCADO, "#007A3D", "supercor exprés", "supercor expres"),
        m("Consum", C.SUPERMERCADO, "#F39200"),
        m("Bonpreu", C.SUPERMERCADO, "#E3000F", "bonpreu esclat", "esclat"),
        m("Caprabo", C.SUPERMERCADO, "#E30613"),
        m("Ahorramas", C.SUPERMERCADO, "#E30613", "ahorra mas"),
        m("Gadis", C.SUPERMERCADO, "#E30613", "gadisa"),
        m("Froiz", C.SUPERMERCADO, "#E30613"),
        m("Covirán", C.SUPERMERCADO, "#E2001A", "coviran"),
        m("Spar", C.SUPERMERCADO, "#E30613", "spar express"),
        m("Condis", C.SUPERMERCADO, "#E30613"),
        m("BM Supermercados", C.SUPERMERCADO, "#E30613", "bm supermercado", mark = "BM"),
        m("Masymas", C.SUPERMERCADO, "#E2001A", "mas y mas", mark = "M+"),
        m("Hiperber", C.SUPERMERCADO, "#E30613"),
        m("El Árbol", C.SUPERMERCADO, "#6AA84F", "el arbol"),
        m("Lupa", C.SUPERMERCADO, "#E30613", "supermercados lupa"),
        m("Family Cash", C.SUPERMERCADO, "#003F8A"),
        m("Makro", C.SUPERMERCADO, "#003D7C"),
        m("Costco", C.SUPERMERCADO, "#E31837"),
        m("Ametller Origen", C.SUPERMERCADO, "#2E7D32", "ametller"),
        m("Veritas", C.SUPERMERCADO, "#7AB929"),
        m("Primaprix", C.SUPERMERCADO, "#E30613"),
        m("Sánchez Romero", C.SUPERMERCADO, "#1B3A5B", "sanchez romero"),
        m("Mercado Barceló", C.SUPERMERCADO, "#1B3A5B", "mercado barcelo"),

        // Comida a domicilio
        m("Glovo", C.COMIDA_DOMICILIO, "#FFC244", "glovoapp", onColor = YELLOW_TEXT),
        m("Just Eat", C.COMIDA_DOMICILIO, "#FF8000", "justeat", "just eat es"),
        m("Uber Eats", C.COMIDA_DOMICILIO, "#06C167", "ubereats", "uber eats es"),
        m("Deliveroo", C.COMIDA_DOMICILIO, "#00CCBC"),
        m("Telepizza", C.COMIDA_DOMICILIO, "#E30613"),
        m("Domino's Pizza", C.COMIDA_DOMICILIO, "#006491", "domino s", "dominos", "dominos pizza", mark = "D"),
        m("Pizza Hut", C.COMIDA_DOMICILIO, "#EE3124"),
        m("Papa John's", C.COMIDA_DOMICILIO, "#0F7A3A", "papa john", "papa johns", mark = "PJ"),
        m("Too Good To Go", C.COMIDA_DOMICILIO, "#00615F", "toogoodtogo", mark = "TG"),

        // Restaurantes y cafeterías
        m("McDonald's", C.RESTAURANTES, "#DA291C", "mcdonald", "mcdonalds", "mc donalds", mark = "M"),
        m("Burger King", C.RESTAURANTES, "#D62300", "burgerking"),
        m("KFC", C.RESTAURANTES, "#A3080C", "kentucky fried chicken"),
        m("Starbucks", C.RESTAURANTES, "#00704A", "starbucks coffee"),
        m("100 Montaditos", C.RESTAURANTES, "#1D1D1B", "cien montaditos", "montaditos", mark = "100"),
        m("VIPS", C.RESTAURANTES, "#E30613", "vips smart"),
        m("Foster's Hollywood", C.RESTAURANTES, "#B71C1C", "foster s hollywood", "fosters hollywood", "foster hollywood", mark = "FH"),
        m("Goiko", C.RESTAURANTES, "#1D1D1B", "goiko grill"),
        m("La Tagliatella", C.RESTAURANTES, "#8B1A1A", "tagliatella"),
        m("Ginos", C.RESTAURANTES, "#1B5E20", "ginos ristorante"),
        m("Rodilla", C.RESTAURANTES, "#E30613"),
        m("Tim Hortons", C.RESTAURANTES, "#C8102E"),
        m("Five Guys", C.RESTAURANTES, "#D2202F"),
        m("Taco Bell", C.RESTAURANTES, "#702082"),
        m("Subway", C.RESTAURANTES, "#008C15"),
        m("Pans & Company", C.RESTAURANTES, "#E30613", "pans company", "pans and company", mark = "P&C"),
        m("Café de Indias", C.RESTAURANTES, "#4E342E", "cafe de indias"),
        m("Granier", C.RESTAURANTES, "#8D6E63", "panes granier", "granier bakery"),
        m("Santagloria", C.RESTAURANTES, "#1D1D1B"),
        m("Tony Roma's", C.RESTAURANTES, "#8B0000", "tony roma s", "tony romas", mark = "TR"),
        m("Lizarran", C.RESTAURANTES, "#7B1F1F"),
        m("Udon", C.RESTAURANTES, "#1D1D1B", "udon asian food"),
        m("Honest Greens", C.RESTAURANTES, "#2E7D32"),
        m("TGB The Good Burger", C.RESTAURANTES, "#1D1D1B", "the good burger", "tgb", mark = "TGB"),
        m("Popeyes", C.RESTAURANTES, "#F25600"),
        m("Dunkin'", C.RESTAURANTES, "#FF671F", "dunkin", "dunkin coffee", "dunkin donuts"),
        m("Manolo Bakes", C.RESTAURANTES, "#E91E63"),
        m("Llaollao", C.RESTAURANTES, "#7CB342", "llao llao"),
        m("Casa Tarradellas", C.SUPERMERCADO, "#E30613", "tarradellas"),

        // Gasolineras
        m("Repsol", C.GASOLINA, "#FF6A13", "repsol e s", "waylet"),
        m("Moeve", C.GASOLINA, "#00A19A", "cepsa", "cepsa e s"),
        m("BP", C.GASOLINA, "#009B3A", "bp oil", "bp estacion"),
        m("Galp", C.GASOLINA, "#F15A22", "galp energia"),
        m("Shell", C.GASOLINA, "#FFD500", onColor = YELLOW_TEXT),
        m("Petronor", C.GASOLINA, "#FF6A13"),
        m("Ballenoil", C.GASOLINA, "#0072BC"),
        m("Plenoil", C.GASOLINA, "#00A651"),
        m("Petroprix", C.GASOLINA, "#E30613"),
        m("Avia", C.GASOLINA, "#E30613", "avia energies"),
        m("Disa", C.GASOLINA, "#004B87"),
        m("Esclatoil", C.GASOLINA, "#E3000F"),
        m("Tamoil", C.GASOLINA, "#E30613"),
        m("Iberdrola recarga", C.GASOLINA, "#5CB82E", "iberdrola ev", "iberdrola recarga", mark = "IR"),
        m("Zunder", C.GASOLINA, "#1D1D1B"),
        m("Tesla Supercharger", C.GASOLINA, "#CC0000", "tesla supercharger", "tesla charging", mark = "T"),

        // Suscripciones
        m("Netflix", C.SUSCRIPCIONES, "#E50914", "netflix com"),
        m("Spotify", C.SUSCRIPCIONES, "#1DB954", "spotify ab", "spotify premium"),
        m("HBO Max", C.SUSCRIPCIONES, "#5822B4", "hbo", "max com", "hbomax", mark = "MAX"),
        m("Disney+", C.SUSCRIPCIONES, "#113CCF", "disney plus", "disneyplus", mark = "D+"),
        m("Prime Video", C.SUSCRIPCIONES, "#00A8E1", "amazon prime", "prime video", "primevideo", "amazon video", mark = "P"),
        m("Apple", C.SUSCRIPCIONES, "#1D1D1F", "apple com", "apple com bill", "itunes", "icloud", "apple music", "apple tv", "apple one", mark = "A"),
        m("Google One", C.SUSCRIPCIONES, "#4285F4", "google storage", "google one"),
        m("YouTube Premium", C.SUSCRIPCIONES, "#FF0000", "youtube", "youtube premium", "youtubepremium", mark = "YT"),
        m("Google Play", C.SUSCRIPCIONES, "#01875F", "google play", "google play apps"),
        m("DAZN", C.SUSCRIPCIONES, "#1D1D1B", onColor = "#F7FF1A"),
        m("Filmin", C.SUSCRIPCIONES, "#1D1D1B"),
        m("Movistar Plus+", C.SUSCRIPCIONES, "#019DF4", "movistar plus", "movistarplus", mark = "M+"),
        m("SkyShowtime", C.SUSCRIPCIONES, "#0A1E5A", "sky showtime"),
        m("Atresplayer", C.SUSCRIPCIONES, "#FF6B00", "atres player"),
        m("Audible", C.SUSCRIPCIONES, "#F7991C"),
        m("Kindle Unlimited", C.SUSCRIPCIONES, "#146EB4", "kindle"),
        m("Storytel", C.SUSCRIPCIONES, "#FF5C28"),
        m("ChatGPT", C.SUSCRIPCIONES, "#10A37F", "openai", "chatgpt subscription", mark = "AI"),
        m("Claude", C.SUSCRIPCIONES, "#D97757", "anthropic", "claude ai"),
        m("Patreon", C.SUSCRIPCIONES, "#FF424D"),
        m("Tidal", C.SUSCRIPCIONES, "#1D1D1B"),
        m("Deezer", C.SUSCRIPCIONES, "#A238FF"),
        m("Twitch", C.SUSCRIPCIONES, "#9146FF"),
        m("Xbox Game Pass", C.SUSCRIPCIONES, "#107C10", "game pass", "xbox game pass", mark = "GP"),
        m("PlayStation Plus", C.SUSCRIPCIONES, "#003791", "ps plus", "playstation plus", mark = "PS+"),
        m("Nintendo Switch Online", C.SUSCRIPCIONES, "#E60012", "nintendo switch online", mark = "NSO"),
        m("Microsoft 365", C.SUSCRIPCIONES, "#D83B01", "microsoft 365", "office 365", "msft", mark = "365"),
        m("Adobe", C.SUSCRIPCIONES, "#FA0F00", "adobe systems", "adobe creative cloud"),
        m("Dropbox", C.SUSCRIPCIONES, "#0061FF"),
        m("Duolingo", C.SUSCRIPCIONES, "#58CC02"),
        m("Tinder", C.SUSCRIPCIONES, "#FE3C72"),
        m("Canva", C.SUSCRIPCIONES, "#00C4CC"),
        m("Crunchyroll", C.SUSCRIPCIONES, "#F47521"),

        // Transporte
        m("Renfe", C.TRANSPORTE, "#8A1D8D", "renfe viajeros", "renfe cercanias"),
        m("Iryo", C.TRANSPORTE, "#C8102E"),
        m("Ouigo", C.TRANSPORTE, "#E6007E"),
        m("Alsa", C.TRANSPORTE, "#00529B"),
        m("Avanza", C.TRANSPORTE, "#E30613"),
        m("BlaBlaCar", C.TRANSPORTE, "#00AFF5", "blablacar"),
        m("Cabify", C.TRANSPORTE, "#7145D6"),
        m("Uber", C.TRANSPORTE, "#000000", "uber bv", "uber trip", "uber rides"),
        m("Bolt", C.TRANSPORTE, "#34D186", "bolt eu", "bolt ride"),
        m("FreeNow", C.TRANSPORTE, "#C8102E", "free now", "freenow", "mytaxi"),
        m("Metro de Madrid", C.TRANSPORTE, "#E30613", "metro madrid", "metro de madrid", mark = "M"),
        m("TMB", C.TRANSPORTE, "#E30613", "transports metropolitans", "tmb metro"),
        m("EMT", C.TRANSPORTE, "#0066B3", "emt madrid", "emt valencia", "emt palma"),
        m("Metrovalencia", C.TRANSPORTE, "#E30613", "fgv", "metro valencia"),
        m("Metro Bilbao", C.TRANSPORTE, "#E30613"),
        m("Tussam", C.TRANSPORTE, "#E30613"),
        m("Iberia", C.TRANSPORTE, "#D7192D", "iberia express", "iberia lineas aereas"),
        m("Vueling", C.TRANSPORTE, "#FFCC00", "vueling airlines", onColor = YELLOW_TEXT),
        m("Ryanair", C.TRANSPORTE, "#073590"),
        m("Air Europa", C.TRANSPORTE, "#0A2F6B"),
        m("easyJet", C.TRANSPORTE, "#FF6600", "easyjet"),
        m("Volotea", C.TRANSPORTE, "#E6007E"),
        m("Binter", C.TRANSPORTE, "#00A651", "binter canarias"),
        m("Aena", C.TRANSPORTE, "#00843D", "aena parking", "aena aeropuertos"),
        m("Empark", C.TRANSPORTE, "#0072BC", "telpark", "empark aparcamientos"),
        m("Saba", C.TRANSPORTE, "#E30613", "saba aparcamientos", "saba parking"),
        m("Parclick", C.TRANSPORTE, "#00B2A9"),
        m("ElParking", C.TRANSPORTE, "#00A0DF", "el parking"),
        m("Via-T", C.TRANSPORTE, "#004B87", "via t", "telepeaje", "autopistas", "abertis", mark = "VT"),
        m("Bicing", C.TRANSPORTE, "#E30613"),
        m("BiciMAD", C.TRANSPORTE, "#0066B3", "bicimad"),
        m("Lime", C.TRANSPORTE, "#00DD00", "lime ride", "limebike", onColor = YELLOW_TEXT),
        m("Cooltra", C.TRANSPORTE, "#1D1D1B", "ecooltra"),
        m("Zity", C.TRANSPORTE, "#00B1EB", "zity by mobilize"),
        m("Free2Move", C.TRANSPORTE, "#1D1D1B", "free2move", "share now", "sharenow", mark = "F2M"),
        m("Norauto", C.TRANSPORTE, "#E30613"),
        m("Feu Vert", C.TRANSPORTE, "#009A44"),
        m("Midas", C.TRANSPORTE, "#FFCC00", onColor = YELLOW_TEXT),
        m("Euromaster", C.TRANSPORTE, "#003D7C"),
        m("Booking.com", C.OCIO, "#003580", "booking com", "booking", mark = "B"),
        m("Airbnb", C.OCIO, "#FF5A5F"),

        // Compras
        m("Amazon", C.COMPRAS, "#FF9900", "amazon es", "amzn", "amzn mktp", "amazon eu", "amazon marketplace", "amazon com", onColor = YELLOW_TEXT),
        m("AliExpress", C.COMPRAS, "#E62E04", "aliexpress", "alibaba"),
        m("Shein", C.COMPRAS, "#1D1D1B"),
        m("Temu", C.COMPRAS, "#FB7701"),
        m("El Corte Inglés", C.COMPRAS, "#007A3D", "el corte ingles", "corte ingles", mark = "ECI"),
        m("Zara", C.COMPRAS, "#1D1D1B"),
        m("Bershka", C.COMPRAS, "#1D1D1B"),
        m("Pull&Bear", C.COMPRAS, "#1D1D1B", "pull bear", "pull and bear", mark = "P&B"),
        m("Stradivarius", C.COMPRAS, "#1D1D1B"),
        m("Massimo Dutti", C.COMPRAS, "#1D1D1B"),
        m("Oysho", C.COMPRAS, "#1D1D1B"),
        m("Lefties", C.COMPRAS, "#E30613"),
        m("Primark", C.COMPRAS, "#0DB4E3"),
        m("Mango", C.COMPRAS, "#1D1D1B"),
        m("H&M", C.COMPRAS, "#E50010", "h m", "hm", "hennes mauritz", mark = "H&M"),
        m("Kiabi", C.COMPRAS, "#0077C8"),
        m("C&A", C.COMPRAS, "#0E4D92", "c a", mark = "C&A"),
        m("Springfield", C.COMPRAS, "#1D1D1B"),
        m("Cortefiel", C.COMPRAS, "#1D1D1B"),
        m("Decathlon", C.COMPRAS, "#0082C3"),
        m("Sprinter", C.COMPRAS, "#E30613"),
        m("JD Sports", C.COMPRAS, "#1D1D1B"),
        m("Foot Locker", C.COMPRAS, "#1D1D1B"),
        m("Nike", C.COMPRAS, "#1D1D1B"),
        m("Adidas", C.COMPRAS, "#1D1D1B"),
        m("MediaMarkt", C.COMPRAS, "#DF0000", "media markt", "mediamarkt"),
        m("PcComponentes", C.COMPRAS, "#FF6000", "pccomponentes", "pc componentes"),
        m("Worten", C.COMPRAS, "#E30613"),
        m("Fnac", C.COMPRAS, "#E1A925", onColor = YELLOW_TEXT),
        m("Apple Store", C.COMPRAS, "#1D1D1F", "apple store", "apple retail", mark = "A"),
        m("Xiaomi", C.COMPRAS, "#FF6900", "mi store"),
        m("Game", C.COMPRAS, "#E30613", "game stores"),
        m("Druni", C.COMPRAS, "#E6007E"),
        m("Primor", C.COMPRAS, "#1D1D1B"),
        m("Sephora", C.COMPRAS, "#1D1D1B"),
        m("Douglas", C.COMPRAS, "#1D1D1B"),
        m("Rituals", C.COMPRAS, "#1D1D1B"),
        m("Kiko Milano", C.COMPRAS, "#1D1D1B", "kiko"),
        m("Mercadona Online", C.SUPERMERCADO, "#00843D", "tienda mercadona"),
        m("Flying Tiger", C.COMPRAS, "#1D1D1B", "tiger", "flying tiger copenhagen"),
        m("Tedi", C.COMPRAS, "#E30613"),
        m("Casa del Libro", C.COMPRAS, "#00843D"),
        m("Imaginarium", C.COMPRAS, "#E6007E"),
        m("Juguettos", C.COMPRAS, "#00A0DF"),
        m("Toys R Us", C.COMPRAS, "#0071CE", "toysrus", mark = "TR"),
        m("Tiendanimal", C.COMPRAS, "#F58220", "tienda animal"),
        m("Kiwoko", C.COMPRAS, "#00A651"),
        m("Vinted", C.COMPRAS, "#007782"),
        m("Wallapop", C.COMPRAS, "#13C1AC"),
        m("eBay", C.COMPRAS, "#E53238", "ebay"),
        m("Zalando", C.COMPRAS, "#FF6900"),
        m("Carrefour Online", C.SUPERMERCADO, "#004E9E", "carrefour es"),

        // Hogar y facturas
        m("IKEA", C.HOGAR, "#0058A3", "ikea iberica", onColor = "#FFDA1A"),
        m("Leroy Merlin", C.HOGAR, "#78BE20"),
        m("Bricomart", C.HOGAR, "#E30613"),
        m("Brico Depôt", C.HOGAR, "#0066B3", "bricodepot", "brico depot", mark = "BD"),
        m("Bauhaus", C.HOGAR, "#E30613"),
        m("AKI", C.HOGAR, "#F58220", "aki bricolaje"),
        m("JYSK", C.HOGAR, "#003F8A"),
        m("Maisons du Monde", C.HOGAR, "#1D1D1B"),
        m("Conforama", C.HOGAR, "#E30613"),
        m("Endesa", C.HOGAR, "#0555FA", "endesa energia"),
        m("Iberdrola", C.HOGAR, "#5CB82E", "iberdrola clientes", "curenergia"),
        m("Naturgy", C.HOGAR, "#004571", "gas natural"),
        m("TotalEnergies", C.HOGAR, "#E30613", "totalenergies", "total energies"),
        m("Holaluz", C.HOGAR, "#FFD200", onColor = YELLOW_TEXT),
        m("Repsol Luz y Gas", C.HOGAR, "#FF6A13", "repsol luz", "repsol electricidad", mark = "RL"),
        m("Canal de Isabel II", C.HOGAR, "#0066B3", "canal isabel", "canal de isabel", mark = "CYII"),
        m("Aigües de Barcelona", C.HOGAR, "#0066B3", "aigues de barcelona", "agbar", mark = "AB"),
        m("Movistar", C.HOGAR, "#019DF4", "telefonica", "telefonica de espana"),
        m("Vodafone", C.HOGAR, "#E60000"),
        m("Orange", C.HOGAR, "#FF7900", onColor = YELLOW_TEXT),
        m("Yoigo", C.HOGAR, "#8C1D82"),
        m("MásMóvil", C.HOGAR, "#FFD100", "masmovil", "mas movil", onColor = YELLOW_TEXT, mark = "MM"),
        m("Digi", C.HOGAR, "#0066B3", "digi spain", "digi mobil"),
        m("Pepephone", C.HOGAR, "#E30613"),
        m("Lowi", C.HOGAR, "#1D1D1B"),
        m("O2", C.HOGAR, "#0019A5", "o2 espana"),
        m("Simyo", C.HOGAR, "#FF6600"),
        m("Jazztel", C.HOGAR, "#FF6600"),
        m("Finetwork", C.HOGAR, "#E6007E"),
        m("Securitas Direct", C.HOGAR, "#E30613", "securitas", "verisure"),
        m("Mapfre", C.HOGAR, "#E30613", "mapfre seguros"),
        m("Mutua Madrileña", C.HOGAR, "#003F8A", "mutua madrilena"),
        m("Línea Directa", C.HOGAR, "#E30613", "linea directa"),

        // Salud
        m("Sanitas", C.SALUD, "#009FDA"),
        m("Adeslas", C.SALUD, "#004B87", "segurcaixa adeslas"),
        m("DKV", C.SALUD, "#00843D", "dkv seguros"),
        m("Asisa", C.SALUD, "#004B87"),
        m("Quirónsalud", C.SALUD, "#004B87", "quironsalud", "quiron"),
        m("Vithas", C.SALUD, "#0066B3"),
        m("HM Hospitales", C.SALUD, "#003F8A", "hm hospitales"),
        m("Vitaldent", C.SALUD, "#009FDA"),
        m("Dentix", C.SALUD, "#00A0DF"),
        m("Multiópticas", C.SALUD, "#E30613", "multiopticas"),
        m("General Óptica", C.SALUD, "#1D1D1B", "general optica"),
        m("Alain Afflelou", C.SALUD, "#1D1D1B", "afflelou"),
        m("Specsavers", C.SALUD, "#00843D"),
        m("Opticalia", C.SALUD, "#E30613"),
        m("Visionlab", C.SALUD, "#003F8A"),
        m("Anicura", C.SALUD, "#00A19A"),
        m("Promofarma", C.SALUD, "#00A19A"),
        m("Atida Mifarma", C.SALUD, "#00A19A", "mifarma", "atida"),

        // Ocio
        m("Cinesa", C.OCIO, "#E30613"),
        m("Yelmo Cines", C.OCIO, "#E30613", "yelmo"),
        m("Kinépolis", C.OCIO, "#E30613", "kinepolis"),
        m("Ocine", C.OCIO, "#E6007E"),
        m("Ticketmaster", C.OCIO, "#026CDF"),
        m("Entradas.com", C.OCIO, "#E30613", "entradas com", mark = "E"),
        m("Fever", C.OCIO, "#1D1D1B", "feverup"),
        m("Steam", C.OCIO, "#1B2838", "steam games", "steampowered", "valve"),
        m("PlayStation Store", C.OCIO, "#003791", "playstation network", "sony interactive", "psn", mark = "PS"),
        m("Nintendo eShop", C.OCIO, "#E60012", "nintendo", mark = "N"),
        m("Xbox", C.OCIO, "#107C10", "microsoft xbox"),
        m("Epic Games", C.OCIO, "#1D1D1B", "epic games store"),
        m("Basic-Fit", C.OCIO, "#FF7900", "basic fit", "basicfit", mark = "BF"),
        m("McFit", C.OCIO, "#E30613"),
        m("Anytime Fitness", C.OCIO, "#5C2D91"),
        m("Holmes Place", C.OCIO, "#1D1D1B"),
        m("Viva Gym", C.OCIO, "#E6007E", "vivagym"),
        m("Altafit", C.OCIO, "#E30613"),
        m("Metropolitan", C.OCIO, "#1D1D1B", "metropolitan spa"),
        m("Loterías", C.OCIO, "#004B87", "loterias", "loterias y apuestas", "selae", mark = "L"),
        m("ONCE", C.OCIO, "#00843D"),
        m("Parque Warner", C.OCIO, "#004B87"),
        m("PortAventura", C.OCIO, "#E30613", "port aventura"),

        // Más supermercados y alimentación
        m("La Sirena", C.SUPERMERCADO, "#003F8A", "la sirena congelados"),
        m("Cash Fresh", C.SUPERMERCADO, "#E30613"),
        m("Dialprix", C.SUPERMERCADO, "#E30613"),
        m("HiperDino", C.SUPERMERCADO, "#E30613", "hiper dino", "superdino"),
        m("Plusfresc", C.SUPERMERCADO, "#7AB929", "plus fresc"),
        m("Vegalsa", C.SUPERMERCADO, "#E30613", "vegalsa eroski"),
        m("Unide", C.SUPERMERCADO, "#E30613"),
        m("Coaliment", C.SUPERMERCADO, "#E30613"),
        m("Amazon Fresh", C.SUPERMERCADO, "#5FA30F", "amazon fresh"),
        m("Mercado de Abastos", C.SUPERMERCADO, "#6AA84F", "mercado abastos"),
        m("Hiber", C.SUPERMERCADO, "#1B5E20"),
        m("Frutas Eloy", C.SUPERMERCADO, "#E53935"),
        m("Carnicas Serrano", C.SUPERMERCADO, "#B71C1C", "carnicas serrano"),
        m("Pescanova", C.SUPERMERCADO, "#003F8A"),

        // Más restaurantes y cafeterías
        m("Carl's Jr.", C.RESTAURANTES, "#FFC72C", "carl s jr", "carls jr", onColor = YELLOW_TEXT, mark = "CJ"),
        m("Wendy's", C.RESTAURANTES, "#E2203D", "wendy s", "wendys"),
        m("Krispy Kreme", C.RESTAURANTES, "#00704A"),
        m("Costa Coffee", C.RESTAURANTES, "#6D1F37"),
        m("Café & Té", C.RESTAURANTES, "#5D4037", "cafe te", "cafe y te"),
        m("Viena Capellanes", C.RESTAURANTES, "#1D1D1B"),
        m("La Sureña", C.RESTAURANTES, "#1D1D1B", "la surena"),
        m("Lateral", C.RESTAURANTES, "#1D1D1B"),
        m("Ribs", C.RESTAURANTES, "#8B0000"),
        m("Muerde la Pasta", C.RESTAURANTES, "#E30613"),
        m("Wok to Walk", C.RESTAURANTES, "#E30613", "woktowalk"),
        m("Sushi Shop", C.RESTAURANTES, "#1D1D1B"),
        m("Bocatta", C.RESTAURANTES, "#E30613"),
        m("Panaria", C.RESTAURANTES, "#8D6E63"),
        m("Pastelería Mallorca", C.RESTAURANTES, "#1D1D1B", "pasteleria mallorca"),
        m("Bacoa", C.RESTAURANTES, "#1D1D1B"),
        m("La Pepita", C.RESTAURANTES, "#E30613"),
        m("Vicio", C.RESTAURANTES, "#E6007E"),
        m("Tommy Mel's", C.RESTAURANTES, "#E30613", "tommy mel s", "tommy mels", mark = "TM"),
        m("Hard Rock Cafe", C.RESTAURANTES, "#1D1D1B"),
        m("Areas", C.RESTAURANTES, "#E30613", "areas sa"),
        m("Pizzería Carlos", C.RESTAURANTES, "#E30613", "pizzeria carlos"),
        m("Alsea", C.RESTAURANTES, "#1D1D1B"),
        m("Sodexo", C.RESTAURANTES, "#2A295C"),
        m("Serunion", C.RESTAURANTES, "#E30613"),
        m("Eurest", C.RESTAURANTES, "#E30613"),
        m("Mascarpone", C.RESTAURANTES, "#1D1D1B"),

        // Más comida a domicilio
        m("Wetaca", C.COMIDA_DOMICILIO, "#1D1D1B"),
        m("Foodtopia", C.COMIDA_DOMICILIO, "#E30613"),
        m("Pedidos Ya", C.COMIDA_DOMICILIO, "#FA0050", "pedidosya"),

        // Moda y complementos
        m("Uniqlo", C.COMPRAS, "#E60012"),
        m("Desigual", C.COMPRAS, "#1D1D1B"),
        m("Women'secret", C.COMPRAS, "#E6007E", "women secret", "womensecret", mark = "WS"),
        m("Tezenis", C.COMPRAS, "#1D1D1B"),
        m("Calzedonia", C.COMPRAS, "#1D1D1B"),
        m("Intimissimi", C.COMPRAS, "#1D1D1B"),
        m("Parfois", C.COMPRAS, "#1D1D1B"),
        m("Bimba y Lola", C.COMPRAS, "#1D1D1B", "bimba lola", mark = "BL"),
        m("Tous", C.COMPRAS, "#8D6E63"),
        m("Pandora", C.COMPRAS, "#1D1D1B"),
        m("Sfera", C.COMPRAS, "#1D1D1B"),
        m("Scalpers", C.COMPRAS, "#1D1D1B"),
        m("El Ganso", C.COMPRAS, "#1D1D1B"),
        m("Lacoste", C.COMPRAS, "#00754A"),
        m("Levi's", C.COMPRAS, "#C41230", "levi s", "levis", mark = "L"),
        m("Guess", C.COMPRAS, "#1D1D1B"),
        m("Tommy Hilfiger", C.COMPRAS, "#002D62"),
        m("Victoria's Secret", C.COMPRAS, "#E6007E", "victoria s secret", "victorias secret", mark = "VS"),
        m("Skechers", C.COMPRAS, "#003F8A"),
        m("Deichmann", C.COMPRAS, "#E30613"),
        m("Mayoral", C.COMPRAS, "#003F8A"),
        m("Merkal", C.COMPRAS, "#E30613"),
        m("Mustang", C.COMPRAS, "#1D1D1B", "mustang shoes"),
        m("Misako", C.COMPRAS, "#1D1D1B"),
        m("Salsa Jeans", C.COMPRAS, "#1D1D1B"),
        m("Pedro del Hierro", C.COMPRAS, "#1D1D1B"),
        m("Hawkers", C.COMPRAS, "#1D1D1B"),
        m("Mr. Wonderful", C.COMPRAS, "#F5C518", "mr wonderful", onColor = YELLOW_TEXT, mark = "MW"),

        // Deporte
        m("Forum Sport", C.COMPRAS, "#E30613"),
        m("Intersport", C.COMPRAS, "#003F8A"),
        m("Deporvillage", C.COMPRAS, "#1D1D1B"),
        m("Tradeinn", C.COMPRAS, "#E30613"),
        m("Base Deportes", C.COMPRAS, "#E30613"),

        // Tecnología
        m("Samsung", C.COMPRAS, "#1428A0", "samsung store"),
        m("Coolmod", C.COMPRAS, "#1D1D1B"),
        m("PC Box", C.COMPRAS, "#E30613", "pcbox"),
        m("Back Market", C.COMPRAS, "#1D1D1B", "backmarket"),
        m("Phone House", C.COMPRAS, "#E30613", "the phone house"),

        // Belleza
        m("Yves Rocher", C.COMPRAS, "#2E7D32"),
        m("The Body Shop", C.COMPRAS, "#004236", "body shop"),
        m("Lush", C.COMPRAS, "#1D1D1B"),
        m("Arenal Perfumerías", C.COMPRAS, "#1D1D1B", "arenal perfumerias", "arenal"),
        m("Marco Aldany", C.COMPRAS, "#1D1D1B"),
        m("Llongueras", C.COMPRAS, "#1D1D1B"),

        // Niños y mascotas
        m("Prénatal", C.COMPRAS, "#00A0DF", "prenatal"),
        m("Bebitus", C.COMPRAS, "#00A0DF"),
        m("Zooplus", C.COMPRAS, "#2E7D32"),
        m("Miscota", C.COMPRAS, "#F58220"),

        // Hogar
        m("Zara Home", C.HOGAR, "#8D6E63", "zara home"),
        m("Kave Home", C.HOGAR, "#1D1D1B"),
        m("Obramat", C.HOGAR, "#E30613"),
        m("Carglass", C.TRANSPORTE, "#E30613"),
        m("Casa Shops", C.HOGAR, "#1D1D1B", "casa shops"),
        m("Sklum", C.HOGAR, "#1D1D1B"),
        m("Leroy Merlin Online", C.HOGAR, "#78BE20", "leroymerlin es"),

        // Luz, gas, agua, teléfono e internet
        m("Octopus Energy", C.HOGAR, "#E6007E"),
        m("Som Energia", C.HOGAR, "#96B633"),
        m("EDP", C.HOGAR, "#E30613", "edp energia", "edp comercializadora"),
        m("Aldro Energía", C.HOGAR, "#E6007E", "aldro", "aldro energia"),
        m("Podo", C.HOGAR, "#1D1D1B", "podo energia"),
        m("Factor Energía", C.HOGAR, "#003F8A", "factor energia"),
        m("Aqualia", C.HOGAR, "#0066B3"),
        m("Emasesa", C.HOGAR, "#0066B3"),
        m("Emaya", C.HOGAR, "#0066B3"),
        m("Avatel", C.HOGAR, "#E6007E"),
        m("Adamo", C.HOGAR, "#1D1D1B"),
        m("Parlem", C.HOGAR, "#E6007E"),
        m("Euskaltel", C.HOGAR, "#E30613"),
        m("R Cable", C.HOGAR, "#E30613", "r cable y telecomunicaciones"),
        m("Telecable", C.HOGAR, "#003F8A"),
        m("Lyca Mobile", C.HOGAR, "#003F8A", "lycamobile"),
        m("Lebara", C.HOGAR, "#003F8A"),
        m("Virgin telco", C.HOGAR, "#E30613", "virgin telco"),
        m("Hits Mobile", C.HOGAR, "#1D1D1B"),
        m("Suop", C.HOGAR, "#1D1D1B"),

        // Seguros
        m("Allianz", C.HOGAR, "#003781"),
        m("AXA", C.HOGAR, "#00008F", "axa seguros"),
        m("Generali", C.HOGAR, "#C21B17"),
        m("Zurich", C.HOGAR, "#1D5AAB", "zurich seguros"),
        m("Reale Seguros", C.HOGAR, "#003F8A", "reale"),
        m("Pelayo", C.HOGAR, "#E30613"),
        m("Santalucía", C.HOGAR, "#003F8A", "santalucia", "santa lucia"),
        m("Ocaso", C.HOGAR, "#003F8A"),
        m("Caser", C.HOGAR, "#E30613", "caser seguros"),
        m("Verti", C.HOGAR, "#6CBE45"),
        m("Direct Seguros", C.HOGAR, "#E30613"),
        m("Génesis", C.HOGAR, "#E30613", "genesis seguros"),

        // Transporte y coche
        m("Hertz", C.TRANSPORTE, "#FFD100", onColor = YELLOW_TEXT),
        m("Avis", C.TRANSPORTE, "#D4002A"),
        m("Europcar", C.TRANSPORTE, "#00843D"),
        m("Sixt", C.TRANSPORTE, "#FF5F00"),
        m("Enterprise", C.TRANSPORTE, "#006B3F", "enterprise rent a car"),
        m("Goldcar", C.TRANSPORTE, "#FFC20E", onColor = YELLOW_TEXT),
        m("Record go", C.TRANSPORTE, "#E30613", "recordgo"),
        m("Centauro", C.TRANSPORTE, "#003F8A"),
        m("OK Mobility", C.TRANSPORTE, "#1D1D1B", "ok mobility", "okmobility"),
        m("Getaround", C.TRANSPORTE, "#B01AA7"),
        m("Monbus", C.TRANSPORTE, "#E30613"),
        m("Arriva", C.TRANSPORTE, "#00A0DF"),
        m("Baleària", C.TRANSPORTE, "#003F8A", "balearia"),
        m("Trasmediterránea", C.TRANSPORTE, "#003F8A", "trasmediterranea", "armas trasmediterranea"),
        m("Naviera Armas", C.TRANSPORTE, "#003F8A"),
        m("Fred. Olsen", C.TRANSPORTE, "#003F8A", "fred olsen"),
        m("Indigo Parking", C.TRANSPORTE, "#4B2A8A", "indigo parking"),
        m("Q-Park", C.TRANSPORTE, "#E30613", "q park", "qpark"),
        m("Interparking", C.TRANSPORTE, "#003F8A"),
        m("Trainline", C.TRANSPORTE, "#00A88F"),
        m("Omio", C.TRANSPORTE, "#1D1D1B"),
        m("Metro de Sevilla", C.TRANSPORTE, "#00843D", "metro sevilla"),
        m("Metro de Málaga", C.TRANSPORTE, "#E30613", "metro malaga"),
        m("Tram", C.TRANSPORTE, "#E30613", "tram barcelona", "tranvia"),
        m("Aurgi", C.TRANSPORTE, "#E30613"),
        m("Confortauto", C.TRANSPORTE, "#003F8A", "confort auto"),

        // Viajes y hoteles
        m("Expedia", C.OCIO, "#FFC72C", onColor = YELLOW_TEXT),
        m("eDreams", C.OCIO, "#003F8A", "edreams"),
        m("Rumbo", C.OCIO, "#E30613"),
        m("Hotels.com", C.OCIO, "#D32F2F", "hotels com", mark = "H"),
        m("NH Hotels", C.OCIO, "#1D1D1B", "nh hotel", "nh collection"),
        m("Meliá", C.OCIO, "#1D1D1B", "melia", "melia hotels"),
        m("Barceló", C.OCIO, "#1D1D1B", "barcelo hotels"),
        m("Iberostar", C.OCIO, "#003F8A"),
        m("RIU", C.OCIO, "#1D1D1B", "riu hotels"),
        m("Paradores", C.OCIO, "#7B1F1F", "parador"),
        m("Civitatis", C.OCIO, "#E30613"),
        m("GetYourGuide", C.OCIO, "#FF5533", "get your guide"),
        m("Atrápalo", C.OCIO, "#E6007E", "atrapalo"),
        m("Groupon", C.OCIO, "#53A318"),
        m("Smartbox", C.OCIO, "#E6007E"),
        m("Wonderbox", C.OCIO, "#1D1D1B"),

        // Ocio, entradas y gimnasios
        m("Eventbrite", C.OCIO, "#F05537"),
        m("Parques Reunidos", C.OCIO, "#003F8A"),
        m("Aquópolis", C.OCIO, "#00A0DF", "aquopolis"),
        m("Cines Renoir", C.OCIO, "#1D1D1B", "renoir"),
        m("Go Fit", C.OCIO, "#E30613", "gofit"),
        m("DiR", C.OCIO, "#E30613", "dir club", "clubs dir"),
        m("Forus", C.OCIO, "#1D1D1B"),
        m("Synergym", C.OCIO, "#1D1D1B"),
        m("Fitness Park", C.OCIO, "#E30613"),

        // Suscripciones
        m("Amazon Music", C.SUSCRIPCIONES, "#25D1DA", "amazon music"),
        m("Paramount+", C.SUSCRIPCIONES, "#0064FF", "paramount plus", "paramount", mark = "P+"),
        m("Rakuten TV", C.SUSCRIPCIONES, "#BF0000", "rakuten"),
        m("Mubi", C.SUSCRIPCIONES, "#1D1D1B"),
        m("Qobuz", C.SUSCRIPCIONES, "#1D1D1B"),
        m("SoundCloud", C.SUSCRIPCIONES, "#FF5500", "soundcloud"),
        m("Google Workspace", C.SUSCRIPCIONES, "#4285F4", "google workspace", "gsuite"),
        m("Notion", C.SUSCRIPCIONES, "#1D1D1B"),
        m("Evernote", C.SUSCRIPCIONES, "#00A82D"),
        m("1Password", C.SUSCRIPCIONES, "#0572EC", "1password", mark = "1P"),
        m("NordVPN", C.SUSCRIPCIONES, "#4687FF", "nordvpn", "nord vpn"),
        m("ExpressVPN", C.SUSCRIPCIONES, "#DA3940", "expressvpn"),
        m("Proton", C.SUSCRIPCIONES, "#6D4AFF", "proton ag", "protonmail"),
        m("Bumble", C.SUSCRIPCIONES, "#FFC629", onColor = YELLOW_TEXT),
        m("Meetic", C.SUSCRIPCIONES, "#E6007E"),
        m("Strava", C.SUSCRIPCIONES, "#FC4C02"),
        m("Headspace", C.SUSCRIPCIONES, "#FF7E1D"),
        m("Calm", C.SUSCRIPCIONES, "#3A6FE0"),
        m("Babbel", C.SUSCRIPCIONES, "#FF6100"),
        m("Busuu", C.SUSCRIPCIONES, "#1F7BF2"),
        m("LinkedIn", C.SUSCRIPCIONES, "#0A66C2", "linkedin premium"),
        m("El País", C.SUSCRIPCIONES, "#1D1D1B", "el pais", "prisa"),
        m("El Mundo", C.SUSCRIPCIONES, "#1D1D1B"),
        m("La Vanguardia", C.SUSCRIPCIONES, "#1D1D1B"),
        m("ABC", C.SUSCRIPCIONES, "#003F8A", "abc es"),
        m("elDiario.es", C.SUSCRIPCIONES, "#1D1D1B", "eldiario es", "eldiario"),
        m("Marca", C.SUSCRIPCIONES, "#E30613", "marca plus"),
        m("Amazon Prime Gaming", C.SUSCRIPCIONES, "#9146FF", "prime gaming"),
        m("EA Play", C.SUSCRIPCIONES, "#FF4747", "electronic arts", "ea play"),
        m("Ubisoft+", C.SUSCRIPCIONES, "#1D1D1B", "ubisoft", mark = "U+"),
        m("Setlist", C.SUSCRIPCIONES, "#1D1D1B"),
    )

    /** Coincidencia de marca: dónde empieza en el texto normalizado, qué alias y qué comercio. */
    internal data class Hit(val merchant: KnownMerchant, val alias: String, val start: Int)

    private val patterns: List<Pair<KnownMerchant, Pair<String, Regex>>> = all.flatMap { km ->
        km.aliases.map { a -> km to (a to Regex("""\b${Regex.escape(a)}\b""")) }
    }

    /** Palabras de pasarelas y datáfonos que a veces van delante del comercio ("PAYPAL *NETFLIX", "SUMUP *BAR PEPE"). */
    private val gatewayPrefix = Regex("""^((paypal|pp|sumup|sq|zettle|izettle|stripe|www|compra|pago|pagado|en|tpv|apple pay|google pay)\s+)+""")

    /** La coincidencia de marca más larga en cualquier parte del texto (para la categoría). */
    internal fun bestHit(normalized: String): Hit? {
        var best: Hit? = null
        for ((km, ap) in patterns) {
            val (alias, re) = ap
            if (best != null && alias.length <= best.alias.length) continue
            val found = re.find(normalized) ?: continue
            best = Hit(km, alias, found.range.first)
        }
        return best
    }

    private val cache = ConcurrentHashMap<String, Any>()
    private val NONE = Any()

    /**
     * El comercio conocido que es [merchant], o null. Solo cuenta si la marca va al principio
     * (quitando pasarelas como "PAYPAL *"): «Bar Orange» no es la operadora Orange.
     */
    fun find(merchant: String): KnownMerchant? {
        if (merchant.isBlank()) return null
        val cached = cache[merchant]
        if (cached != null) return cached as? KnownMerchant
        val key = gatewayPrefix.replace(Categorizer.normalize(merchant), "")
        val hit = bestHit(key)?.takeIf { it.start == 0 }?.merchant
        if (cache.size > 2_000) cache.clear()
        cache[merchant] = hit ?: NONE
        return hit
    }

    /** Nombre limpio para un pago detectado: la marca si se reconoce, si no el texto tal cual. */
    fun displayName(merchant: String): String = find(merchant)?.name ?: merchant
}
