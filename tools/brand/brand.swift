// Genera la identidad de MiDinero: el € en Plus Jakarta Sans para los vectores de Android,
// el icono de la ficha de Google Play (512×512) y el gráfico de funciones (1024×500).
// Uso: swift tools/brand/brand.swift <carpeta de salida>
import AppKit
import CoreText

let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
let out = URL(fileURLWithPath: CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "build/brand")
try? FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)

/** Peso de Plus Jakarta Sans de la app (los ficheros se cargan directamente: todos se llaman «Regular» por dentro). */
func font(_ weight: String, _ size: CGFloat) -> CTFont {
    let url = root.appendingPathComponent("app/src/main/res/font/jakarta_\(weight).ttf") as CFURL
    let d = (CTFontManagerCreateFontDescriptorsFromURL(url) as! [CTFontDescriptor]).first!
    return CTFontCreateWithFontDescriptor(d, size, nil)
}
let extraBold = "extrabold"

let ink = CGColor(red: 0x14 / 255, green: 0x13 / 255, blue: 0x18 / 255, alpha: 1)
let inkHi = CGColor(red: 0x23 / 255, green: 0x22 / 255, blue: 0x2A / 255, alpha: 1)
let lime = CGColor(red: 0xD4 / 255, green: 0xF5 / 255, blue: 0x4A / 255, alpha: 1)
let white = CGColor(gray: 1, alpha: 1)

// ---------- Geometría común (en el lienzo de 108×108 de los iconos adaptativos) ----------
let ringRadius: CGFloat = 23
let ringWidth: CGFloat = 7.5
let arcStart: CGFloat = -90          // arriba
let arcSweep: CGFloat = 290          // en el sentido de las agujas del reloj
let euroHeight: CGFloat = 24         // alto del € en unidades del lienzo

/** Contorno del € de Plus Jakarta Sans ExtraBold, centrado en (54, 54) con alto [euroHeight] (y hacia abajo). */
func euroPath() -> CGPath {
    let f = font(extraBold, 100)
    var ch: [UniChar] = Array("€".utf16)
    var glyph = CGGlyph()
    CTFontGetGlyphsForCharacters(f, &ch, &glyph, 1)
    let raw = CTFontCreatePathForGlyph(f, glyph, nil)!
    let b = raw.boundingBoxOfPath
    let s = euroHeight / b.height
    // Centro óptico: el € pesa a la izquierda por las barras, se desplaza un poco a la derecha
    var t = CGAffineTransform(translationX: 54 + 0.6, y: 54)
        .scaledBy(x: s, y: -s)
        .translatedBy(x: -b.midX, y: -b.midY)
    return raw.copy(using: &t)!
}

/** CGPath → pathData de un VectorDrawable. */
func pathData(_ p: CGPath) -> String {
    var s = ""
    func n(_ v: CGFloat) -> String { String(format: "%.2f", v).replacingOccurrences(of: ".00", with: "") }
    p.applyWithBlock { e in
        let pt = e.pointee.points
        switch e.pointee.type {
        case .moveToPoint: s += "M\(n(pt[0].x)),\(n(pt[0].y))"
        case .addLineToPoint: s += "L\(n(pt[0].x)),\(n(pt[0].y))"
        case .addQuadCurveToPoint: s += "Q\(n(pt[0].x)),\(n(pt[0].y)) \(n(pt[1].x)),\(n(pt[1].y))"
        case .addCurveToPoint: s += "C\(n(pt[0].x)),\(n(pt[0].y)) \(n(pt[1].x)),\(n(pt[1].y)) \(n(pt[2].x)),\(n(pt[2].y))"
        case .closeSubpath: s += "Z"
        @unknown default: break
        }
    }
    return s
}

func rad(_ d: CGFloat) -> CGFloat { d * .pi / 180 }
func ringPoint(_ deg: CGFloat) -> CGPoint { CGPoint(x: 54 + ringRadius * cos(rad(deg)), y: 54 + ringRadius * sin(rad(deg))) }

/** pathData del arco (para strokeWidth/strokeLineCap en el vector). */
func arcPathData() -> String {
    let a = ringPoint(arcStart), mid = ringPoint(arcStart + arcSweep / 2), b = ringPoint(arcStart + arcSweep)
    func n(_ v: CGFloat) -> String { String(format: "%.2f", v) }
    // Dos medios arcos: un solo arco de más de 180° es ambiguo en algunos renderizadores
    return "M\(n(a.x)),\(n(a.y))A\(n(ringRadius)),\(n(ringRadius)) 0,0 1,\(n(mid.x)),\(n(mid.y))A\(n(ringRadius)),\(n(ringRadius)) 0,0 1,\(n(b.x)),\(n(b.y))"
}

/** Dibuja el símbolo en un contexto ya escalado al lienzo de 108 (y hacia abajo). */
func drawMark(_ c: CGContext, track: Bool = true) {
    if track {
        c.setStrokeColor(CGColor(gray: 1, alpha: 0.10)); c.setLineWidth(ringWidth)
        c.strokeEllipse(in: CGRect(x: 54 - ringRadius, y: 54 - ringRadius, width: ringRadius * 2, height: ringRadius * 2))
    }
    c.setStrokeColor(lime); c.setLineWidth(ringWidth); c.setLineCap(.round)
    c.addArc(center: CGPoint(x: 54, y: 54), radius: ringRadius, startAngle: rad(arcStart), endAngle: rad(arcStart + arcSweep), clockwise: false)
    c.strokePath()
    c.setFillColor(white); c.addPath(euroPath()); c.fillPath()
}

/** Fondo de tinta con el brillo lima arriba a la derecha (como la tarjeta de Inicio). */
func drawBackground(_ c: CGContext, _ rect: CGRect, glowAt: CGPoint, glowRadius: CGFloat) {
    let g = CGGradient(colorsSpace: nil, colors: [inkHi, ink] as CFArray, locations: [0, 1])!
    c.drawLinearGradient(g, start: CGPoint(x: rect.minX, y: rect.minY), end: CGPoint(x: rect.maxX, y: rect.maxY), options: [])
    let glow = CGGradient(colorsSpace: nil, colors: [CGColor(red: 0xD4 / 255, green: 0xF5 / 255, blue: 0x4A / 255, alpha: 0.22), CGColor(red: 0xD4 / 255, green: 0xF5 / 255, blue: 0x4A / 255, alpha: 0)] as CFArray, locations: [0, 1])!
    c.drawRadialGradient(glow, startCenter: glowAt, startRadius: 0, endCenter: glowAt, endRadius: glowRadius, options: [])
}

func bitmap(_ w: Int, _ h: Int, _ draw: (CGContext) -> Void) -> CGImage {
    let c = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    // Coordenadas con y hacia abajo, como Android
    c.translateBy(x: 0, y: CGFloat(h)); c.scaleBy(x: 1, y: -1)
    c.setShouldAntialias(true); c.interpolationQuality = .high
    draw(c)
    return c.makeImage()!
}

func save(_ img: CGImage, _ name: String) {
    let url = out.appendingPathComponent(name)
    let d = CGImageDestinationCreateWithURL(url as CFURL, "public.png" as CFString, 1, nil)!
    CGImageDestinationAddImage(d, img, nil); CGImageDestinationFinalize(d)
    print("→ \(url.path)")
}

func text(_ c: CGContext, _ s: String, _ f: CTFont, _ color: CGColor, x: CGFloat, baseline: CGFloat) {
    let line = CTLineCreateWithAttributedString(NSAttributedString(string: s, attributes: [.init(kCTFontAttributeName as String): f, .init(kCTForegroundColorAttributeName as String): color]))
    c.saveGState(); c.textMatrix = .identity
    c.translateBy(x: x, y: baseline); c.scaleBy(x: 1, y: -1)
    c.textPosition = .zero; CTLineDraw(line, c)
    c.restoreGState()
}

// ---------- Salidas ----------
// 1) Datos para los vectores de Android
let vectors = """
euro=\(pathData(euroPath()))
arc=\(arcPathData())
"""
try! vectors.write(to: out.appendingPathComponent("paths.txt"), atomically: true, encoding: .utf8)
print(vectors)

// 2) Icono de la ficha de Google Play: 512×512 a sangre (Play redondea las esquinas)
save(bitmap(512, 512) { c in
    drawBackground(c, CGRect(x: 0, y: 0, width: 512, height: 512), glowAt: CGPoint(x: 430, y: 70), glowRadius: 420)
    // El símbolo ocupa la zona segura del icono adaptativo (66/108) ampliada un poco para la tienda
    let s: CGFloat = 512 / 108 * 1.12
    c.translateBy(x: 256 - 54 * s, y: 256 - 54 * s); c.scaleBy(x: s, y: s)
    drawMark(c)
}, "icon.png")

// 3) Gráfico de funciones 1024×500
func feature(tagline: String, sub: String, name: String) {
    save(bitmap(1024, 500) { c in
        drawBackground(c, CGRect(x: 0, y: 0, width: 1024, height: 500), glowAt: CGPoint(x: 900, y: 40), glowRadius: 620)
        // Curva de gasto del mes, como en Inicio, al fondo a la derecha
        c.saveGState()
        let pts: [CGPoint] = [CGPoint(x: 600, y: 492), CGPoint(x: 680, y: 470), CGPoint(x: 740, y: 452), CGPoint(x: 800, y: 448),
                              CGPoint(x: 860, y: 420), CGPoint(x: 920, y: 406), CGPoint(x: 975, y: 384), CGPoint(x: 1040, y: 372)]
        let curve = CGMutablePath(); curve.move(to: pts[0])
        for i in 1..<pts.count { let p0 = pts[i - 1], p1 = pts[i]; curve.addCurve(to: p1, control1: CGPoint(x: (p0.x + p1.x) / 2, y: p0.y), control2: CGPoint(x: (p0.x + p1.x) / 2, y: p1.y)) }
        let area = curve.mutableCopy()!; area.addLine(to: CGPoint(x: 1040, y: 520)); area.addLine(to: CGPoint(x: 560, y: 520)); area.closeSubpath()
        c.addPath(area); c.clip()
        let fade = CGGradient(colorsSpace: nil, colors: [CGColor(red: 0xD4 / 255, green: 0xF5 / 255, blue: 0x4A / 255, alpha: 0.16), CGColor(red: 0xD4 / 255, green: 0xF5 / 255, blue: 0x4A / 255, alpha: 0)] as CFArray, locations: [0, 1])!
        c.drawLinearGradient(fade, start: CGPoint(x: 0, y: 370), end: CGPoint(x: 0, y: 500), options: [])
        c.restoreGState()
        c.setStrokeColor(CGColor(red: 0xD4 / 255, green: 0xF5 / 255, blue: 0x4A / 255, alpha: 0.45)); c.setLineWidth(4); c.setLineCap(.round)
        c.addPath(curve); c.strokePath()

        // Icono a la izquierda, como baldosa redondeada
        let tile = CGRect(x: 72, y: 110, width: 280, height: 280)
        c.saveGState()
        c.setShadow(offset: CGSize(width: 0, height: 18), blur: 40, color: CGColor(gray: 0, alpha: 0.45))
        c.addPath(CGPath(roundedRect: tile, cornerWidth: 64, cornerHeight: 64, transform: nil)); c.setFillColor(ink); c.fillPath()
        c.restoreGState()
        c.saveGState()
        c.addPath(CGPath(roundedRect: tile, cornerWidth: 64, cornerHeight: 64, transform: nil)); c.clip()
        drawBackground(c, tile, glowAt: CGPoint(x: tile.maxX - 40, y: tile.minY + 30), glowRadius: 260)
        let s = tile.width / 108 * 1.12
        c.translateBy(x: tile.midX - 54 * s, y: tile.midY - 54 * s); c.scaleBy(x: s, y: s)
        drawMark(c)
        c.restoreGState()
        c.setStrokeColor(CGColor(gray: 1, alpha: 0.08)); c.setLineWidth(1.5)
        c.addPath(CGPath(roundedRect: tile.insetBy(dx: 0.75, dy: 0.75), cornerWidth: 64, cornerHeight: 64, transform: nil)); c.strokePath()

        // Textos
        text(c, name, font(extraBold, 96), white, x: 400, baseline: 236)
        text(c, tagline, font("bold", 38), lime, x: 408, baseline: 300)
        text(c, sub, font("medium", 26), CGColor(gray: 1, alpha: 0.62), x: 408, baseline: 350)
    }, "featureGraphic-\(name == "MiDinero" ? tagline.hasPrefix("Tus") ? "es" : "en" : "x").png")
}
feature(tagline: "Tus gastos, claros.", sub: "Se apuntan solos · Sin cuentas · Sin anuncios", name: "MiDinero")
feature(tagline: "Your spending, clear.", sub: "Logs itself · No account · No ads", name: "MiDinero")

// 4) Vistas previas para revisar: máscara circular y squircle del lanzador, y el reloj
save(bitmap(640, 220) { c in
    c.setFillColor(CGColor(gray: 0.93, alpha: 1)); c.fill(CGRect(x: 0, y: 0, width: 640, height: 220))
    let size: CGFloat = 180
    for (i, shape) in ["circle", "squircle", "watch"].enumerated() {
        let r = CGRect(x: 20 + CGFloat(i) * 210, y: 20, width: size, height: size)
        c.saveGState()
        let p = shape == "squircle" ? CGPath(roundedRect: r, cornerWidth: 48, cornerHeight: 48, transform: nil) : CGPath(ellipseIn: r, transform: nil)
        c.addPath(p); c.clip()
        // El lanzador muestra 72 de los 108 del lienzo
        let s = size / 72
        c.translateBy(x: r.minX - 18 * s, y: r.minY - 18 * s); c.scaleBy(x: s, y: s)
        drawBackground(c, CGRect(x: 0, y: 0, width: 108, height: 108), glowAt: CGPoint(x: 86, y: 22), glowRadius: 90)
        drawMark(c)
        c.restoreGState()
    }
}, "preview.png")
