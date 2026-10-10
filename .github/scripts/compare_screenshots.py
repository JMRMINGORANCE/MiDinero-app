"""Compara las capturas de esta compilación con las de la anterior y avisa de las pantallas que cambian.

Uso: compare_screenshots.py <carpeta_anterior> <carpeta_nueva>
Deja en <carpeta_nueva>/diff/ una imagen «antes | ahora | diferencias» por cada pantalla que cambia,
escribe cambios.md y emite avisos de GitHub Actions. No hace fallar la compilación.
"""
import os
import sys
from PIL import Image, ImageChops

WARN_PERCENT = 1.5  # por encima de esto, aviso
NOTE_PERCENT = 0.2  # por debajo, se considera igual (hora, fecha...)

prev_dir, new_dir = sys.argv[1], sys.argv[2]
diff_dir = os.path.join(new_dir, "diff")
os.makedirs(diff_dir, exist_ok=True)
rows = []
for name in sorted(f for f in os.listdir(new_dir) if f.endswith(".png")):
    new = Image.open(os.path.join(new_dir, name)).convert("RGB")
    prev_path = os.path.join(prev_dir, name)
    if not os.path.exists(prev_path):
        rows.append((name, None, "nueva"))
        continue
    prev = Image.open(prev_path).convert("RGB")
    if prev.size != new.size:
        rows.append((name, 100.0, "cambia de tamaño"))
        continue
    delta = ImageChops.difference(prev, new).convert("L").point(lambda v: 255 if v > 24 else 0)
    changed = delta.histogram()[255]
    pct = changed * 100.0 / (new.size[0] * new.size[1])
    if pct < NOTE_PERCENT:
        continue
    overlay = Image.blend(new, Image.new("RGB", new.size, (255, 255, 255)), 0.6)
    overlay.paste(Image.new("RGB", new.size, (229, 72, 77)), mask=delta)
    sheet = Image.new("RGB", (new.size[0] * 3 + 40, new.size[1]), (40, 40, 40))
    for i, img in enumerate([prev, new, overlay]):
        sheet.paste(img, (i * (new.size[0] + 20), 0))
    sheet.save(os.path.join(diff_dir, name))
    rows.append((name, pct, "cambia"))

summary = ["## Capturas que cambian respecto a la compilación anterior", ""]
if not rows:
    summary.append("Ninguna pantalla ha cambiado.")
else:
    summary += ["| Pantalla | Píxeles distintos | Estado |", "|---|---|---|"]
    for name, pct, state in rows:
        summary.append(f"| {name} | {'—' if pct is None else f'{pct:.1f} %'} | {state} |")
        if pct is not None and pct >= WARN_PERCENT:
            print(f"::warning title=Pantalla cambiada::{name} cambia un {pct:.1f} % (ver diff/{name} en la rama screenshots)")
text = "\n".join(summary) + "\n"
open(os.path.join(new_dir, "cambios.md"), "w").write(text)
step = os.environ.get("GITHUB_STEP_SUMMARY")
if step:
    open(step, "a").write(text)
print(text)
