package com.midinero.control.domain

/** Un icono con las palabras con que se busca (en español y en inglés). */
data class EmojiEntry(val emoji: String, val keywords: String)

data class EmojiGroup(val es: String, val en: String, val entries: List<EmojiEntry>) {
    val title: String get() = tr(es, en)
}

/**
 * Iconos para las categorías, por temas y con buscador. Además se puede escribir cualquier emoji con el teclado.
 * Cada línea: el emoji y sus palabras de búsqueda.
 */
object EmojiCatalog {
    private fun group(es: String, en: String, lines: String) = EmojiGroup(
        es, en,
        lines.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val emoji = line.substringBefore(' ')
            EmojiEntry(emoji, Categorizer.normalize(line.substringAfter(' ', "")))
        },
    )

    val groups: List<EmojiGroup> = listOf(
        group("Comida y bebida", "Food and drink", """
            🛒 supermercado compra carro super supermarket groceries cart
            🍽️ restaurante comer plato restaurant dinner
            🍔 hamburguesa comida rapida burger fast food
            🍕 pizza
            🌮 taco mexicano mexican
            🌯 burrito kebab durum wrap
            🍣 sushi japones japanese
            🍜 ramen fideos noodles asiatico asian
            🥡 comida para llevar takeaway chino chinese
            🥗 ensalada saludable salad healthy
            🥪 bocadillo sandwich
            🥐 cruasan panaderia croissant bakery bolleria
            🥖 pan barra panaderia bread bakery
            🍰 tarta pasteleria cake postre dessert
            🧁 magdalena cupcake dulce sweets
            🍦 helado heladeria ice cream
            🍫 chocolate dulces sweets chuches
            🍬 caramelo chuches candy
            🍿 palomitas cine popcorn
            🍳 desayuno huevo breakfast cocinar cooking
            🥩 carne carniceria meat butcher
            🐟 pescado pescaderia fish
            🍎 fruta fruteria fruit manzana apple
            🥦 verdura verduleria vegetables
            🧀 queso cheese
            ☕ cafe cafeteria coffee
            🍵 te infusion tea
            🧋 bubble tea batido
            🥤 refresco bebida soda drink
            🍺 cerveza bar caña beer
            🍻 cervezas amigos copas drinks
            🍷 vino bodega wine
            🍸 cocktail copa coctel bar
            🥂 brindis celebracion cava champagne
            🍾 champan fiesta party
            💧 agua water
            🛵 comida a domicilio reparto glovo delivery moto
            🧑‍🍳 cocinero chef
        """),
        group("Compras", "Shopping", """
            🛍️ compras tiendas shopping bolsas
            👕 ropa camiseta clothes
            👖 pantalon vaqueros jeans
            👗 vestido dress moda fashion
            👟 zapatillas deporte sneakers
            👠 zapatos tacon shoes
            👜 bolso handbag
            🎒 mochila colegio backpack school
            🕶️ gafas de sol sunglasses
            👓 gafas optica glasses optician
            ⌚ reloj watch
            💍 joya anillo joyeria jewellery ring
            💄 maquillaje cosmetica makeup beauty
            💅 manicura uñas nails
            🧴 cremas higiene drogueria toiletries
            🧼 jabon limpieza soap cleaning
            🪒 afeitado barberia shave
            💇 peluqueria corte pelo hairdresser haircut
            📦 paquete amazon envio parcel shipping
            🏬 centro comercial grandes almacenes mall
            🎁 regalo regalos gift present
            💐 flores floristeria flowers
            🧸 juguetes peluche toys
            📚 libros libreria books
            🖊️ papeleria boligrafo stationery
            🖥️ ordenador informatica computer
            💻 portatil laptop tecnologia tech
            📱 movil telefono phone mobile
            🎧 auriculares headphones musica
            📷 camara fotos camera
            🎮 videojuegos consola games console
            🪑 muebles silla furniture
            🛋️ sofa muebles salon sofa
        """),
        group("Casa", "Home", """
            🏠 casa hogar home house
            🏡 casa jardin house garden
            🏢 piso edificio comunidad flat building
            🔑 alquiler llaves rent keys
            🏦 hipoteca banco mortgage bank
            💡 luz electricidad electricity light
            🔥 gas calefaccion heating
            🚿 agua ducha water shower
            🌐 internet fibra wifi
            📺 television tele tv
            📞 telefono fijo landline
            🧹 limpieza escoba cleaning
            🧺 lavanderia colada laundry
            🔧 reparaciones arreglos herramientas repairs tools
            🔨 bricolaje obras reforma diy
            🪴 plantas jardineria plants
            🛏️ cama dormitorio bed
            🛁 baño bath
            🧯 seguro hogar insurance
            🛡️ seguro proteccion insurance alarma
            🗑️ basura tasas impuestos rubbish
            🧾 facturas recibos bills receipts
        """),
        group("Transporte", "Transport", """
            🚗 coche car
            ⛽ gasolina gasolinera combustible fuel petrol
            🔌 cargar coche electrico charging ev
            🅿️ parking aparcamiento
            🛣️ peaje autopista toll motorway
            🔩 taller mecanico garage
            🚕 taxi uber cabify
            🚌 autobus bus
            🚇 metro subway underground
            🚆 tren renfe train
            🚲 bicicleta bici bike
            🛴 patinete scooter
            🏍️ moto motorbike
            ✈️ avion vuelo viaje flight plane travel
            🚢 barco ferry crucero ship
            🧳 maleta viajes equipaje luggage travel
            🏨 hotel alojamiento hotel accommodation
            🏕️ camping acampada
            🗺️ mapa excursion map trip
        """),
        group("Ocio", "Leisure", """
            🎉 fiesta ocio party fun
            🎬 cine pelicula cinema movie
            🎭 teatro theatre
            🎤 karaoke concierto concert
            🎵 musica spotify music
            🎟️ entradas eventos tickets
            🎳 bolos bowling
            🎲 juegos de mesa board games
            🎰 apuestas loteria casino betting lottery
            🎨 arte pintura art
            📸 fotografia photography
            🏖️ playa vacaciones beach holidays
            ⛷️ esqui nieve ski
            🏞️ naturaleza parque nature park
            🎡 feria parque atracciones fair
            🎯 aficiones hobby
            🧩 puzle puzzle
            🃏 cartas cards
            📰 periodico revista newspaper
            📖 lectura reading
        """),
        group("Deporte y salud", "Sport and health", """
            🏋️ gimnasio pesas gym
            🏃 correr running
            ⚽ futbol football soccer
            🏀 baloncesto basketball
            🎾 tenis padel tennis
            🏊 natacion piscina swimming pool
            🧘 yoga pilates
            🚴 ciclismo cycling
            ⛳ golf
            💊 farmacia medicinas pharmacy medicine
            🩺 medico consulta doctor
            🦷 dentista dental dentist
            🏥 hospital clinica
            🧠 psicologo terapia therapy
            👁️ oftalmologo vista eyes
            💉 vacuna analisis vaccine
            🩹 tiritas botiquin plasters
            💆 masaje fisio massage physio
            🧖 spa relax
        """),
        group("Personas y familia", "People and family", """
            👶 bebe niños baby kids
            🍼 biberon bebe baby bottle
            🧒 niños hijos kids children
            🎓 colegio estudios universidad school education
            ✏️ material escolar school supplies
            👨‍👩‍👧 familia family
            ❤️ pareja amor love partner
            💒 boda wedding
            🎂 cumpleaños birthday
            👵 abuelos mayores grandparents
            🤝 amigos compartido friends shared
            🧑‍💼 trabajo oficina work office
            🙏 donaciones ong caridad donations charity
            ⛪ iglesia church
        """),
        group("Animales", "Pets", """
            🐶 perro mascota dog pet
            🐱 gato mascota cat pet
            🐾 mascotas veterinario pets vet
            🐰 conejo rabbit
            🐦 pajaro bird
            🐠 peces acuario fish aquarium
            🐴 caballo horse
            🦴 comida mascota pet food
        """),
        group("Dinero y trabajo", "Money and work", """
            💶 euros dinero money cash
            💵 billetes efectivo cash notes
            💰 ahorro bolsa dinero savings money bag
            🪙 monedas coins
            💳 tarjeta credito card
            🏧 cajero atm
            📈 inversiones bolsa investments stocks
            📉 perdidas loss
            🧮 contabilidad gestoria accounting
            📊 finanzas finance
            🏛️ impuestos hacienda taxes
            💼 trabajo negocio work business
            🧑‍💻 freelance autonomo
            🎁 bonus extra
            💸 gastos pagos spending payments
            🤑 premio prize
            📄 contrato documentos contract
            🔁 suscripciones recurrente subscriptions recurring
            📮 correos envios post
        """),
        group("Objetos y símbolos", "Objects and symbols", """
            ⭐ favorito estrella star favourite
            ✨ especial brillo special
            🔔 avisos notifications
            🎈 globo balloon
            🧧 sobre regalo envelope
            🔒 seguridad security
            ⚙️ ajustes otros settings misc
            📌 importante important
            🏷️ etiqueta oferta tag sale
            ❓ otros desconocido other unknown
            ❗ urgente urgent
            ✅ hecho done
            🟢 verde green
            🔵 azul blue
            🟣 morado purple
            🟠 naranja orange
            🔴 rojo red
            ⚫ negro black
            ♻️ reciclaje segunda mano recycling second hand
            🌱 ecologico eco
            ☀️ verano sol summer
            ❄️ invierno winter
            🎄 navidad christmas
            🎃 halloween
            🌍 mundo world
        """),
    )

    /** Los iconos que coinciden con la búsqueda (todas las palabras), sin repetir. */
    fun search(query: String): List<EmojiEntry> {
        val words = Categorizer.normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return groups.flatMap { it.entries }
            .filter { e -> words.all { w -> e.keywords.split(' ').any { it.startsWith(w) } } }
            .distinctBy { it.emoji }
    }
}
