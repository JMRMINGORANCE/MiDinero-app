"""Descarga el logo de cada comercio del catálogo (tools/logos/domains.tsv) y lo guarda en
app/src/main/assets/logos/<clave>.webp, para que vaya dentro de la app (que no tiene permiso de Internet).

Lo ejecuta el workflow «Logos de comercios» en GitHub Actions. Por defecto solo busca los que aún no tienen
logo (los que ya están se revisaron a mano); con --all los vuelve a descargar todos.

Fuentes, por orden (la tercera columna de domains.tsv puede limitarlas, p. ej. «play,wikidata»):
  web       icono de la propia web (apple-touch-icon)
  gstatic   icono grande que Google guarda de la web
  google    servicio de favicons de Google
  ddg       servicio de favicons de DuckDuckGo
  play      icono de su app en Google Play (búsqueda por nombre; se comprueba que el título coincida)
  wikidata  logo oficial en Wikidata / Wikimedia Commons (por web oficial o por nombre)
  wikidata:Q…  esa ficha concreta de Wikidata
  simpleicons:<icono>:<color>  logo de Simple Icons (https://simpleicons.org) con ese color
  play=<texto>  buscar en Google Play con ese texto (el título de la app tiene que empezar por el nombre)
  same:<comercio>  el mismo logo que otro comercio del catálogo
  -         ninguna: revisado a mano, se queda con sus siglas

Deja tools/logos/preview.png (los nuevos, con su nombre) y tools/logos/report.txt para revisarlos.
"""
import io
import json
import re
import sys
import unicodedata
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/assets/logos"
SIZE = 96
UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36"
WIKI_UA = "MiDineroLogos/1.0 (https://github.com/JMRMINGORANCE/MiDinero)"
ALL_SOURCES = ["web", "gstatic", "google", "ddg", "play", "wikidata"]


def key(name: str) -> str:
    """Igual que MerchantLogos.key() en la app."""
    s = unicodedata.normalize("NFD", name.lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", "_", s).strip("_")


def fetch(url: str, ua: str = UA, limit: int = 3_000_000) -> bytes | None:
    try:
        req = urllib.request.Request(url, headers={"User-Agent": ua, "Accept-Language": "es-ES,es;q=0.9"})
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.read(limit) if r.status == 200 else None
    except Exception:
        return None


def image(url: str, ua: str = UA) -> Image.Image | None:
    data = fetch(url, ua)
    if not data:
        return None
    try:
        img = Image.open(io.BytesIO(data))
        if img.format == "ICO" and img.info.get("sizes"):
            img.size = max(img.info["sizes"], key=lambda s: s[0] * s[1])
        img.load()
        return img.convert("RGBA")
    except Exception:
        return None


# --- Fuentes -------------------------------------------------------------------------------------------

def hosts(domain: str) -> list[str]:
    bare = domain.removeprefix("www.")
    return [bare] if bare.count(".") > 1 else [f"www.{bare}", bare]


def src_web(name, domain):
    for h in hosts(domain):
        for path in ("apple-touch-icon.png", "apple-touch-icon-precomposed.png"):
            yield f"https://{h}/{path}", None


def src_gstatic(name, domain):
    for h in hosts(domain):
        yield f"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&url=https://{h}&size=256", None


def src_google(name, domain):
    yield f"https://www.google.com/s2/favicons?domain={domain.removeprefix('www.')}&sz=256", None


def src_ddg(name, domain):
    yield f"https://icons.duckduckgo.com/ip3/{domain.removeprefix('www.')}.ico", None


def similar(a: str, b: str) -> bool:
    """El título de la app empieza por el nombre del comercio (o al revés), sin acentos ni signos."""
    x, y = key(a).replace("_", ""), key(b).replace("_", "")
    return bool(x) and bool(y) and (y.startswith(x) or x.startswith(y))


def src_play(name, domain, query=None):
    q = urllib.parse.quote(query or name)
    html = fetch(f"https://play.google.com/store/search?q={q}&c=apps&hl=es&gl=ES")
    if not html:
        return
    ids = list(dict.fromkeys(re.findall(r"/store/apps/details\?id=([\w.]+)", html.decode("utf-8", "ignore"))))[:4]
    for app_id in ids:
        page = fetch(f"https://play.google.com/store/apps/details?id={app_id}&hl=es&gl=ES")
        if not page:
            continue
        text = page.decode("utf-8", "ignore")
        title = re.search(r'<meta property="og:title" content="([^"]+)"', text)
        icon = re.search(r'<meta property="og:image" content="([^"]+)"', text)
        if title and icon and similar(name, title.group(1).split(" - ")[0]):
            url = re.sub(r"=[ws]\d+[^\"]*$", "", icon.group(1)) + "=s256"
            yield url, f"play:{app_id}"
            return


def wikidata_logo(entity: str) -> str | None:
    data = fetch(f"https://www.wikidata.org/wiki/Special:EntityData/{entity}.json", WIKI_UA)
    if not data:
        return None
    try:
        claims = json.loads(data)["entities"][entity]["claims"]
        file = claims["P154"][0]["mainsnak"]["datavalue"]["value"]
    except Exception:
        return None
    return "https://commons.wikimedia.org/wiki/Special:FilePath/" + urllib.parse.quote(file.replace(" ", "_")) + "?width=256"


def src_wikidata(name, domain):
    # 1) Por web oficial (lo más fiable)
    if domain and domain != "-":
        bare = domain.removeprefix("www.")
        sites = " ".join(f"<{p}{h}{s}>" for p in ("https://", "http://") for h in (f"www.{bare}", bare) for s in ("", "/"))
        query = f"SELECT ?item WHERE {{ VALUES ?site {{ {sites} }} ?item wdt:P856 ?site . ?item wdt:P154 ?logo . }} LIMIT 1"
        data = fetch("https://query.wikidata.org/sparql?format=json&query=" + urllib.parse.quote(query), WIKI_UA)
        try:
            rows = json.loads(data)["results"]["bindings"] if data else []
        except Exception:
            rows = []
        if rows:
            entity = rows[0]["item"]["value"].rsplit("/", 1)[-1]
            url = wikidata_logo(entity)
            if url:
                yield url, f"wikidata:{entity}"
                return
    # 2) Por nombre: solo si la etiqueta coincide
    data = fetch(
        "https://www.wikidata.org/w/api.php?action=wbsearchentities&format=json&language=es&uselang=es&type=item&limit=5&search="
        + urllib.parse.quote(name),
        WIKI_UA,
    )
    try:
        results = json.loads(data)["search"] if data else []
    except Exception:
        results = []
    for r in results:
        if key(r.get("label", "")) == key(name):
            url = wikidata_logo(r["id"])
            if url:
                yield url, f"wikidata:{r['id']} ({r.get('description', '')})"
                return


SOURCES = {"web": src_web, "gstatic": src_gstatic, "google": src_google, "ddg": src_ddg, "play": src_play, "wikidata": src_wikidata}


def best(row) -> tuple[Image.Image, str] | None:
    name, domain, sources = row
    found = None
    for source in sources:
        if source.startswith("same:"):
            other = OUT / f"{key(source[5:])}.webp"
            if other.exists():
                return Image.open(other).convert("RGBA"), source
            continue
        if source.startswith("play="):
            for url, label in src_play(name, domain, source[5:]):
                img = image(url)
                if img is not None and min(img.size) >= 96:
                    return img, label
            continue
        if source.startswith("simpleicons:"):
            img = simple_icon(*source.split(":")[1:3])
            if img is not None:
                return img, source
            continue
        if source.startswith("wikidata:Q"):
            # Ficha de Wikidata indicada a mano
            url = wikidata_logo(source.split(":", 1)[1])
            img = image(url, WIKI_UA) if url else None
            if img is not None:
                return img, source
            continue
        if source not in SOURCES:
            continue
        if source in ("web", "gstatic", "google", "ddg") and (not domain or domain == "-"):
            continue
        for url, label in SOURCES[source](name, domain):
            ua = WIKI_UA if source == "wikidata" else UA
            img = image(url, ua)
            if img is None:
                continue
            w = min(img.size) if source != "wikidata" else max(img.size)
            if w >= 96:
                return img, label or url
            if w >= 48 and (found is None or w > min(found[0].size)):
                found = (img, label or url)
    return found


def simple_icon(slug: str, color: str) -> Image.Image | None:
    """Logo de la colección abierta Simple Icons (SVG de un color) pintado con el color de la marca."""
    import cairosvg

    svg = fetch(f"https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/{slug}.svg")
    if not svg:
        return None
    svg = svg.decode().replace("<path ", f'<path fill="#{color}" ')
    png = cairosvg.svg2png(bytestring=svg.encode(), output_width=240, output_height=240)
    img = Image.open(io.BytesIO(png)).convert("RGBA")
    canvas = Image.new("RGBA", (288, 288), (0, 0, 0, 0))
    canvas.paste(img, (24, 24), img)
    return canvas


def square(img: Image.Image) -> Image.Image:
    """Recorta los bordes transparentes y lo centra en un cuadrado de SIZE px."""
    bbox = img.getchannel("A").getbbox()
    if bbox:
        img = img.crop(bbox)
    w, h = img.size
    side = max(w, h)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(img, ((side - w) // 2, (side - h) // 2))
    return canvas.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    refresh = "--all" in sys.argv
    OUT.mkdir(parents=True, exist_ok=True)
    rows = []
    for line in (ROOT / "tools/logos/domains.tsv").read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        parts = [p.strip() for p in line.split("\t")]
        name, domain = parts[0], parts[1] if len(parts) > 1 else "-"
        sources = parts[2].split(",") if len(parts) > 2 and parts[2] else ALL_SOURCES
        if sources == ["-"]:
            continue
        if refresh or not (OUT / f"{key(name)}.webp").exists():
            rows.append((name, domain, sources))
    print(f"Buscando {len(rows)} logos")
    with ThreadPoolExecutor(max_workers=12) as pool:
        results = list(pool.map(best, rows))
    added, missing = [], []
    for (name, domain, _), result in zip(rows, results):
        if result is None:
            missing.append((name, domain))
            print(f"SIN LOGO  {name} ({domain})")
            continue
        img, label = result
        target = OUT / f"{key(name)}.webp"
        square(img).save(target, "WEBP", quality=88, method=6)
        added.append((name, target, label))
        print(f"ok {name} <- {label}")
    # Hoja de revisión de los nuevos, con su nombre
    cols = 10
    sheet = Image.new("RGB", (cols * 130, max(1, (len(added) + cols - 1) // cols) * 110), (235, 235, 235))
    draw = ImageDraw.Draw(sheet)
    for i, (name, path, _) in enumerate(added):
        x, y = (i % cols) * 130, (i // cols) * 110
        tile = Image.open(path).convert("RGBA").resize((72, 72))
        bg = Image.new("RGB", (72, 72), "white")
        bg.paste(tile, (0, 0), tile)
        sheet.paste(bg, (x + 29, y + 6))
        draw.text((x + 4, y + 84), name[:20], fill="black")
    sheet.save(ROOT / "tools/logos/preview.png")
    (ROOT / "tools/logos/report.txt").write_text(
        f"{len(added)} nuevos, {len(missing)} sin logo\n\n# Nuevos\n"
        + "\n".join(f"{n}\t{label}" for n, _, label in added)
        + "\n\n# Sin logo\n" + "\n".join(f"{n}\t{d}" for n, d in missing) + "\n",
        encoding="utf-8",
    )
    print(f"\n{len(added)} nuevos, {len(missing)} sin logo")


if __name__ == "__main__":
    sys.exit(main())
