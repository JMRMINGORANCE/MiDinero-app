"""Sube los .aab del móvil y del reloj a Google Play (API de publicación) en una sola edición.

Uso: play_upload.py <móvil.aab> <reloj.aab>
Variables: PLAY_SERVICE_ACCOUNT_JSON (clave de la cuenta de servicio), PLAY_TRACK (canal del móvil, «alpha» por
defecto), PLAY_WEAR_TRACK (canal del reloj; si no existe se busca el canal de Wear OS que ya tenga versiones).
Las notas de la versión salen de fastlane/metadata/android/<idioma>/changelogs/default.txt.
"""
import json
import os
import sys
from pathlib import Path
from urllib.parse import quote

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

PACKAGE = "com.midinero.control"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"
UPLOAD = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}"


def check(response):
    if not response.ok:
        sys.exit(f"Error de Google Play ({response.status_code}): {response.text}")
    return response.json()


def release_notes():
    notes = []
    for path in sorted(Path("fastlane/metadata/android").glob("*/changelogs/default.txt")):
        text = path.read_text(encoding="utf-8").strip()
        if text:
            notes.append({"language": path.parent.parent.name, "text": text[:500]})
    return notes


def pick_wear_track(tracks, wanted):
    if wanted in tracks:
        return wanted
    # El canal cerrado del reloj tiene el nombre que se le dio en Play Console: se elige el de Wear OS con versiones
    used = [t for t, releases in tracks.items() if t.startswith("wear:") and t != "wear:production" and releases]
    if len(used) == 1:
        return used[0]
    sys.exit(f"No encuentro el canal del reloj «{wanted}». Canales: {', '.join(tracks)}. Pon el bueno en la variable PLAY_WEAR_TRACK.")


def main():
    phone_aab, wear_aab = sys.argv[1], sys.argv[2]
    info = json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"])
    credentials = service_account.Credentials.from_service_account_info(
        info, scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    session = AuthorizedSession(credentials)

    edit = check(session.post(f"{API}/edits"))["id"]
    tracks = {
        t["track"]: t.get("releases", [])
        for t in check(session.get(f"{API}/edits/{edit}/tracks")).get("tracks", [])
    }
    phone_track = os.environ.get("PLAY_TRACK") or "alpha"
    if phone_track not in tracks:
        sys.exit(f"No existe el canal del móvil «{phone_track}». Canales: {', '.join(tracks)}. Ponlo en la variable PLAY_TRACK.")
    wear_track = pick_wear_track(tracks, os.environ.get("PLAY_WEAR_TRACK") or "wear:Reloj - prueba cerrada")
    notes = release_notes()
    version = os.environ.get("MIDINERO_VERSION", "")

    for aab, track in ((phone_aab, phone_track), (wear_aab, wear_track)):
        with open(aab, "rb") as f:
            bundle = check(session.post(
                f"{UPLOAD}/edits/{edit}/bundles", params={"uploadType": "media"}, data=f,
                headers={"Content-Type": "application/octet-stream"}, timeout=600,
            ))
        code = str(bundle["versionCode"])
        release = {"name": version or code, "versionCodes": [code], "status": "completed"}
        if notes:
            release["releaseNotes"] = notes
        check(session.put(f"{API}/edits/{edit}/tracks/{quote(track, safe='')}", json={"track": track, "releases": [release]}))
        print(f"{Path(aab).name} (versionCode {code}) → {track}")

    check(session.post(f"{API}/edits/{edit}:commit"))
    print("Enviado a Google Play")


if __name__ == "__main__":
    main()
