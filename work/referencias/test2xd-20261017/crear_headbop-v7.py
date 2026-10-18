"""V7: dos parpadeos lentos mediante piezas oculares nativas sobre la V6.

No cambia el motor, la geometría, la música ni las claves de movimiento V6.
Necesita tres PNG 1254² alineados con cabeza.png; no edita imágenes.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import struct
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "proyecto-headbop-v6" / "headbop-test2xd.royer"
SOURCE_SHA256 = "a48b38588220bbd57cb0ceb089609f9611f3bfa484ed3efbf7816d498aff43f1"
ASSETS = ROOT / "assets-v7"
OUTPUT = ROOT / "proyecto-headbop-v7"
BLINK_STARTS_US = (8_150_000, 12_050_000)
BLINK_DURATION_US = 660_000
EYE_REGIONS = {
    "Ojo izquierdo": [0.410, 0.630, 0.595, 0.775],
    "Ojo derecho": [0.675, 0.505, 0.800, 0.690],
}
# El estado inferior permanece opaco debajo del siguiente: al fundir el
# párpado cerrado no reaparece el ojo abierto a través de dos capas a medias.
STATES = (
    {"file": "ojos-65.png", "name": "Parpadeo · 65% abierto",
     "offsets": [(0, 0.0), (60_000, 1.0), (600_000, 1.0), (660_000, 0.0)]},
    {"file": "ojos-30.png", "name": "Parpadeo · 30% abierto",
     "offsets": [(90_000, 0.0), (130_000, 1.0), (500_000, 1.0), (540_000, 0.0)]},
    {"file": "ojos-cerrados.png", "name": "Parpadeo · ojos cerrados",
     "offsets": [(200_000, 0.0), (240_000, 1.0), (340_000, 1.0), (400_000, 0.0)]},
)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def dump(path: Path, obj) -> None:
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def rgba_png_data(path: Path) -> bytes:
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    assert data[12:16] == b"IHDR", path
    width, height, depth, color = struct.unpack(">IIBB", data[16:26])
    assert (width, height) == (1254, 1254), (str(path), width, height)
    assert depth == 8 and color == 6, (str(path), depth, color)
    return data


def relative_encuadre(opacity: float) -> dict:
    return {"ajuste": "ENCAJAR", "escala": 1.0, "x": 0.0, "y": 0.0,
            "rotacion": 0.0, "opacidad": opacity, "espejo": False,
            "recorte": [0.0, 0.0, 0.0, 0.0]}


def opacity_keys(state: dict, layer_start: int, duration: int) -> list[dict]:
    pairs = [(0, 0.0), (duration - 1, 0.0)]
    for blink in BLINK_STARTS_US:
        pairs.extend((blink + offset - layer_start, opacity)
                     for offset, opacity in state["offsets"])
    keys = [{"t": t, "encuadre": relative_encuadre(opacity), "curva": "SUAVE"}
            for t, opacity in sorted(pairs)]
    assert len(keys) == 10 and len({key["t"] for key in keys}) == 10
    assert keys[0]["t"] == 0 and keys[-1]["t"] == duration - 1
    assert all(0 <= key["t"] < duration for key in keys)
    return keys


def config() -> dict:
    return {
        "version": "V7", "fuente": str(SOURCE.relative_to(ROOT)),
        "sha256Fuente": SOURCE_SHA256,
        "archivoAssets": str(ASSETS.relative_to(ROOT)),
        "regionesUv": EYE_REGIONS,
        "parpadeos": [{"inicioUs": start, "finUs": start + BLINK_DURATION_US,
                        "duracionUs": BLINK_DURATION_US,
                        "ojosCerradosDesdeUs": start + 240_000,
                        "ojosCerradosHastaUs": start + 340_000}
                       for start in BLINK_STARTS_US],
        "estados": [{**state, "offsets": [{"t": t, "opacidad": opacity}
                                            for t, opacity in state["offsets"]]}
                    for state in STATES],
        "movimiento": "Copiar sin cambios Cabeza id6: encuadre, claves, HeadBop y tiempos",
        "composicion": "Tres capas sobre Cabeza y debajo de Flequillo; dos piezas UV por capa",
        "soloOpacidadPiezas": True,
        "deformacionEscala": False,
        "notas": "El PNG abierto original permanece de base; la transición usa dos intermedios "
                 "y un párpado cerrado, sin aplastar ni estirar la cabeza.",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--draft", action="store_true", help="Escribe configuración; espera los PNG")
    args = parser.parse_args()
    assert sha(SOURCE) == SOURCE_SHA256, "V6 no coincide con el archivo aprobado"
    preserved = {str(p.relative_to(ROOT)): sha(p) for p in (
        SOURCE, ROOT / "validacion-v6" / "headbop-v6.mp4",
        ROOT / "proyecto-headbop-v4" / "headbop-test2xd.royer",
        ROOT / "borradores" / "V4-aprobada" / "headbop-test2xd-v4.royer",
        ROOT / "borradores" / "V4-aprobada" / "headbop-v4.mp4",
    ) if p.exists()}
    OUTPUT.mkdir(parents=True, exist_ok=True)
    dump(OUTPUT / "ajustes-parpadeo-v7.json", config())
    if args.draft:
        print(json.dumps({"borrador": str(OUTPUT), "faltanAssets": [
            str(ASSETS / state["file"]) for state in STATES
            if not (ASSETS / state["file"]).exists()]}, ensure_ascii=False))
        return

    new_media = {f"medios/000{index + 9:02d}.png": rgba_png_data(ASSETS / state["file"])
                 for index, state in enumerate(STATES)}
    with zipfile.ZipFile(SOURCE) as source_zip:
        assert source_zip.testzip() is None
        original = json.loads(source_zip.read("proyecto.json"))
        project = copy.deepcopy(original)
        assert len(original["capas"]) == 7 and original["siguienteId"] == 10
        head_layer = next(layer for layer in original["capas"] if layer["clip"]["id"] == 6)
        head = head_layer["clip"]
        assert head_layer["fila"] == 3 and head_layer["inicio"] == 700_000
        assert head["origen"]["nombre"] == "Cabeza.png"
        assert head["origen"]["ancho"] == head["origen"]["alto"] == 1254
        assert head["piezas"] == [] and head["headBop"]["naturalidad"] == 0.8
        assert head["desde"] == 0 and head["velocidad"] == 1.0
        duration = head["hasta"]
        for layer in project["capas"]:
            if layer["fila"] > head_layer["fila"]:
                layer["fila"] += len(STATES)

        overlays = []
        for index, state in enumerate(STATES):
            layer = copy.deepcopy(head_layer)
            clip = layer["clip"]
            layer["fila"] = head_layer["fila"] + index + 1
            clip["id"] = original["siguienteId"] + index
            uri = f"medios/000{index + 9:02d}.png"
            clip["origen"]["uri"] = uri
            clip["origen"]["nombre"] = state["name"] + ".png"
            clip["origen"]["bytes"] = len(new_media[uri])
            keys = opacity_keys(state, head_layer["inicio"], duration)
            clip["piezas"] = [
                {"nombre": name, "region": dict(zip(
                    ("izquierda", "arriba", "derecha", "abajo"), region)),
                 "encuadre": relative_encuadre(0.0), "claves": copy.deepcopy(keys)}
                for name, region in EYE_REGIONS.items()
            ]
            # Únicas diferencias frente a Cabeza: identidad, fuente y piezas.
            for key in head:
                if key not in {"id", "origen", "piezas"}:
                    assert clip[key] == head[key], (state["file"], key)
            assert layer["inicio"] == head_layer["inicio"]
            overlays.append(layer)
        project["capas"].extend(overlays)
        project["siguienteId"] += len(overlays)

        # Las siete capas existentes sólo cambian fila cuando estaban encima.
        for layer, old in zip(project["capas"][:7], original["capas"]):
            check = copy.deepcopy(layer)
            check["fila"] = old["fila"]
            assert check == old, old["clip"]["id"]
        assert project["principal"] == original["principal"]
        assert project["audios"] == original["audios"]
        assert project["salida"] == original["salida"]
        assert project["formato"] == original["formato"]
        assert project["fondo"] == original["fondo"]
        ids = [clip["id"] for clip in project["principal"]] + [
            layer["clip"]["id"] for layer in project["capas"]] + [
            audio["id"] for audio in project["audios"]]
        assert len(set(ids)) == len(ids)
        assert max(ids) < project["siguienteId"]

        target = OUTPUT / "headbop-test2xd.royer"
        temp = target.with_suffix(".royer.part")
        with zipfile.ZipFile(temp, "w") as target_zip:
            for entry in source_zip.infolist():
                data = json.dumps(project, ensure_ascii=False, separators=(",", ":")).encode("utf-8") \
                    if entry.filename == "proyecto.json" else source_zip.read(entry.filename)
                target_zip.writestr(copy.copy(entry), data)
            for name, data in new_media.items():
                assert name not in source_zip.namelist(), name
                target_zip.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED)
        temp.replace(target)
        original_media = []
        with zipfile.ZipFile(target) as target_zip:
            assert target_zip.testzip() is None
            assert json.loads(target_zip.read("proyecto.json")) == project
            for name in source_zip.namelist():
                if name == "proyecto.json":
                    continue
                data = target_zip.read(name)
                assert data == source_zip.read(name), name
                original_media.append({"ruta": name, "bytes": len(data),
                                       "sha256": hashlib.sha256(data).hexdigest(), "identicoV6": True})
            for name, data in new_media.items():
                assert target_zip.read(name) == data

    for relative, expected in preserved.items():
        assert sha(ROOT / relative) == expected, relative
    original_keys = sum(len(clip.get("claves", [])) for clip in original["principal"] + [
        layer["clip"] for layer in original["capas"]])
    inherited_keys = sum(len(layer["clip"]["claves"]) for layer in overlays)
    opacity_key_count = sum(len(piece["claves"]) for layer in overlays for piece in layer["clip"]["piezas"])
    assert original_keys == 72 and inherited_keys == 36 and opacity_key_count == 60
    assert len(project["capas"]) == 10 and len(original_media) == 9
    dump(OUTPUT / "headbop-proyecto.json", project)
    dump(OUTPUT / "manifiesto-headbop-v7.json", {
        "version": "V7", "fuente": str(SOURCE.relative_to(ROOT)), "sha256Fuente": SOURCE_SHA256,
        "archivo": target.name, "sha256": sha(target), "bytes": target.stat().st_size,
        "configuracion": config(), "capas": 10, "capasV6Conservadas": 7,
        "nuevasCapas": [{"id": layer["clip"]["id"], "fila": layer["fila"],
                         "nombre": layer["clip"]["origen"]["nombre"],
                         "inicio": layer["inicio"], "headBopIdenticoCabeza": True,
                         "clavesMovimientoIdenticasCabeza": True,
                         "numeroPiezasUv": len(layer["clip"]["piezas"])} for layer in overlays],
        "clavesV6Conservadas": original_keys, "clavesMovimientoHeredadasOverlays": inherited_keys,
        "nuevasClavesSoloOpacidad": opacity_key_count,
        "mediosV6Conservados": original_media,
        "nuevosMedios": [{"ruta": name, "bytes": len(data),
                         "sha256": hashlib.sha256(data).hexdigest(), "dimensiones": [1254, 1254]}
                        for name, data in new_media.items()],
        "versionesAnterioresIntactas": preserved,
        "validacion": {"zipCRC": True, "mediosIdenticosV6": True,
                       "capasExistentesIdenticasExceptoFilas": True,
                       "movimientoCabezaSinCambios": True, "sinDeformacionEscala": True,
                       "musicaIntroDuracionSalidaIdenticas": True,
                       "exportadoEnApp": False, "revisionVisualNativa": False,
                       "pruebaFluidez": False},
        "limitaciones": "La verificación de geometría y hashes no sustituye revisar "
                        "colores, bordes y expresión de los párpados en una exportación nativa.",
    })
    (OUTPUT / "README.md").write_text(
        "# Head bop V7 · parpadeo lento\n\n"
        "Parte del proyecto V6 y conserva todos sus medios, música, intro, duración, salida "
        "y movimiento. V4 y su borrador aprobado siguen intactos.\n\n"
        "Añade tres capas de estados oculares por encima de Cabeza y debajo de Flequillo. "
        "Cada una copia íntegramente los tiempos, el encuadre, las doce claves de movimiento "
        "y la configuración HeadBop de Cabeza. Las seis piezas UV oculares animan solamente "
        "su opacidad: no hay escala, desplazamiento ni giro relativo. Esto mantiene los ojos "
        "pegados a la misma cabeza sin aplastarla.\n\n"
        "Hay dos parpadeos: 8.150–8.810 s y 12.050–12.710 s. Cada uno dura 0.660 s: "
        "cierre en 0.240 s, pausa de 0.100 s con ojos cerrados y recuperación en 0.320 s. "
        "El original abierto se conserva debajo; dos estados intermedios evitan el salto "
        "directo de abierto a cerrado. No hay parpadeos en la intro.\n\n"
        "Las siete capas previas sólo cambian de fila para dejar espacio a las tres nuevas. "
        "Las 72 claves previas quedan intactas; las capas de ojos heredan 36 claves de movimiento "
        "y sus seis piezas añaden 60 claves exclusivamente de opacidad. Los nueve medios V6 "
        "son idénticos byte por byte. La configuración y el manifiesto incluyen tiempos y hashes.\n\n"
        "Regenerar: `python crear_headbop-v7.py`; `--draft` prepara la configuración sin exigir "
        "los PNG. Los nuevos PNG deben ser RGBA de 1254 × 1254, alineados con Cabeza.png. "
        "El script no modifica ni recorta las imágenes: los recortes son piezas nativas del proyecto.\n\n"
        "Verificado: CRC, identidad de medios previos, preservación de movimiento y estructura "
        "nativa del proyecto. Pendiente: exportar desde la app y revisar expresión/bordes. "
        "No se ha medido fluidez ni se afirma todavía validación visual.\n", encoding="utf-8")
    print(json.dumps({"proyectoV7": str(target), "sha256": sha(target), "capas": 10,
                      "mediosV6Identicos": len(original_media), "clavesV6Intactas": original_keys,
                      "clavesOpacidad": opacity_key_count,
                      "parpadeos": config()["parpadeos"], "versionesAnterioresIntactas": True},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
