"""V8: parpadeo lento con nueve poses reales y transición continua.

Construye a partir de V6; no altera arte, movimiento, música ni geometría.
Sólo cambia la opacidad de piezas oculares nativas. Los PNG son externos.
"""
from __future__ import annotations

import argparse
import bisect
import copy
import hashlib
import json
import math
import struct
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "proyecto-headbop-v6" / "headbop-test2xd.royer"
SOURCE_SHA256 = "a48b38588220bbd57cb0ceb089609f9611f3bfa484ed3efbf7816d498aff43f1"
OUTPUT = ROOT / "proyecto-headbop-v8"
BLINK_STARTS_US = (8_150_000, 12_050_000)
CLOSE_US = 240_000
HOLD_US = 100_000
OPEN_US = 320_000
BLINK_DURATION_US = CLOSE_US + HOLD_US + OPEN_US
OPENINGS = (90, 80, 65, 55, 45, 30, 20, 10, 0)
# Raster filenames are generation labels, not measured openness. Inspection of
# both eyes found the 45-labelled drawing more open than the 55-labelled one.
# Preserve the files intact and order them by verified visible iris aperture.
RASTER_LABEL_BY_OPENING = {55: 45, 45: 55}
EYE_REGIONS = {
    "Ojo izquierdo": [0.410, 0.630, 0.595, 0.775],
    "Ojo derecho": [0.675, 0.505, 0.800, 0.690],
}


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def dump(path: Path, obj) -> None:
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def inverse_smoothstep(value: float) -> float:
    """Invert 3u² − 2u³ so pose times follow one global smooth gesture."""
    assert 0.0 <= value <= 1.0
    if value in (0.0, 1.0):
        return value
    lo, hi = 0.0, 1.0
    for _ in range(60):
        middle = (lo + hi) * 0.5
        if middle * middle * (3.0 - 2.0 * middle) < value:
            lo = middle
        else:
            hi = middle
    return (lo + hi) * 0.5


def close_time(opening: int) -> int:
    return round(CLOSE_US * inverse_smoothstep(1.0 - opening / 100.0))


def open_time(opening: int) -> int:
    return CLOSE_US + HOLD_US + round(OPEN_US * inverse_smoothstep(opening / 100.0))


def state_offsets(index: int) -> list[tuple[int, float]]:
    """Only adjacent poses contribute; hidden lower poses are disabled.

    Each close stage fades the next upper drawing over the opaque lower one.
    After coverage that lower drawing turns off in 1 us while fully hidden.
    During opening it turns back on, also hidden, 1 us before it is revealed.
    The visible alpha is always linear; the 1 us keys never change the image.
    """
    count = len(OPENINGS)
    opening = OPENINGS[index]
    previous = 100 if index == 0 else OPENINGS[index - 1]
    c0 = close_time(previous)
    c1 = close_time(opening)
    o0 = open_time(opening)
    o1 = open_time(previous)
    if index == count - 1:
        return [(c0, 0.0), (c1, 1.0), (o0, 1.0), (o1, 0.0)]
    covered = close_time(OPENINGS[index + 1])
    reveal = open_time(OPENINGS[index + 1])
    return [(c0, 0.0), (c1, 1.0), (covered, 1.0), (covered + 1, 0.0),
            (reveal - 1, 0.0), (reveal, 1.0), (o0, 1.0), (o1, 0.0)]


def states() -> list[dict]:
    result = []
    for index, opening in enumerate(OPENINGS):
        if opening in (65, 30):
            source = ROOT / "assets-v7" / f"ojos-{opening}.png"
        elif opening == 0:
            source = ROOT / "assets-v7" / "ojos-cerrados.png"
        else:
            raster_label = RASTER_LABEL_BY_OPENING.get(opening, opening)
            source = ROOT / "assets-v8" / f"ojos-{raster_label:02d}.png"
        result.append({"apertura": opening, "archivo": str(source.relative_to(ROOT)),
                       "nombre": f"Parpadeo · {opening}% abierto" if opening else "Parpadeo · ojos cerrados",
                       "offsets": state_offsets(index)})
    return result


def rgba_png_data(path: Path) -> bytes:
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n" and data[12:16] == b"IHDR", path
    width, height, depth, color = struct.unpack(">IIBB", data[16:26])
    assert (width, height, depth, color) == (1254, 1254, 8, 6), (str(path), width, height, depth, color)
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
    # LINEAL is intentional: SUAVE would pause at every intermediate drawing.
    keys = [{"t": t, "encuadre": relative_encuadre(opacity), "curva": "LINEAL"}
            for t, opacity in sorted(pairs)]
    assert len({key["t"] for key in keys}) == len(keys)
    assert keys[0]["t"] == 0 and keys[-1]["t"] == duration - 1
    assert all(0 <= key["t"] < duration for key in keys)
    return keys


def interpolated(points: list[tuple[int, float]], t: float) -> float:
    if t < points[0][0] or t > points[-1][0]:
        return 0.0
    index = bisect.bisect_right([p[0] for p in points], t) - 1
    if index == len(points) - 1:
        return points[-1][1]
    a, b = points[index], points[index + 1]
    return a[1] + (b[1] - a[1]) * (t - a[0]) / (b[0] - a[0])


def evaluate(t: float) -> dict:
    alphas = [interpolated(state_offsets(index), t) for index in range(len(OPENINGS))]
    transmission = 1.0
    weights = [0.0] * len(OPENINGS)
    for index in reversed(range(len(OPENINGS))):
        weights[index] = alphas[index] * transmission
        transmission *= 1.0 - alphas[index]
    effective = transmission * 100.0 + sum(a * b for a, b in zip(OPENINGS, weights))
    return {"tUs": t, "alpha": alphas, "pesosVisibles": weights,
            "pesoOjoOriginal": transmission, "aperturaCompuesta": effective,
            "posesActivadas": sum(a > 1e-9 for a in alphas),
            "posesContribuyentes": sum(w > 1e-9 for w in weights) + (transmission > 1e-9),
            "sumaPesos": transmission + sum(weights)}


def validate_timing() -> dict:
    # Include all integer microsecond boundaries and 0.5 us offsets around the
    # covered-layer toggles; the extra third active overlay is fully occluded.
    toggles = sorted({t for index in range(len(OPENINGS)) for t, _ in state_offsets(index)})
    samples = [evaluate(t) for t in sorted(set(range(0, BLINK_DURATION_US + 1, 1000)) |
                                             {max(0, t + dt) for t in toggles for dt in (-0.5, 0, 0.5)})]
    assert all(abs(s["sumaPesos"] - 1.0) < 1e-12 for s in samples)
    assert max(s["posesContribuyentes"] for s in samples) <= 2
    assert max(s["posesActivadas"] for s in samples) <= 3
    # Alpha is piecewise linear: support can change only at these keys. Sample
    # both sides of every key plus all millisecond samples, without storing a
    # 660,001-element list of nine-pose vectors.
    integer_times = sorted(set(range(0, BLINK_DURATION_US + 1, 1000)) |
                           {max(0, t + dt) for t in toggles for dt in (-1, 0, 1)})
    assert max(evaluate(t)["posesActivadas"] for t in integer_times) <= 2
    close = [evaluate(t)["aperturaCompuesta"] for t in range(0, CLOSE_US + 1, 1000)]
    opened = [evaluate(t)["aperturaCompuesta"] for t in range(CLOSE_US + HOLD_US, BLINK_DURATION_US + 1, 1000)]
    assert all(a > b for a, b in zip(close, close[1:])), "Hay una pausa durante el cierre"
    assert all(a < b for a, b in zip(opened, opened[1:])), "Hay una pausa durante la apertura"
    assert all(evaluate(t)["aperturaCompuesta"] == 0 for t in (240000, 250000, 300000, 340000))
    assert evaluate(0)["aperturaCompuesta"] == 100 and evaluate(BLINK_DURATION_US)["aperturaCompuesta"] == 100
    # Every native 30 fps frame inside a moving phase must advance the pose.
    frames = [evaluate(frame * 1_000_000 / 30) for frame in range(21)]
    for a, b in zip(frames, frames[1:]):
        if a["tUs"] < b["tUs"] <= CLOSE_US:
            assert a["aperturaCompuesta"] > b["aperturaCompuesta"]
        if CLOSE_US + HOLD_US <= a["tUs"] < b["tUs"] <= BLINK_DURATION_US:
            assert a["aperturaCompuesta"] < b["aperturaCompuesta"]
    actual_frames = []
    for start in BLINK_STARTS_US:
        blink_frames = [{"cuadro": frame, "tiempoGlobalUs": frame * 1_000_000 / 30,
                         **evaluate(frame * 1_000_000 / 30 - start)}
                        for frame in range(math.ceil(start * 30 / 1_000_000),
                                           math.floor((start + BLINK_DURATION_US) * 30 / 1_000_000) + 1)]
        for a, b in zip(blink_frames, blink_frames[1:]):
            if a["tUs"] < b["tUs"] <= CLOSE_US:
                assert a["aperturaCompuesta"] > b["aperturaCompuesta"]
            if CLOSE_US + HOLD_US <= a["tUs"] < b["tUs"]:
                assert a["aperturaCompuesta"] < b["aperturaCompuesta"]
        actual_frames.append({"inicioUs": start, "cuadros": blink_frames})
    return {"interpolacion": "LINEAL entre poses; nodos temporales inverseSmoothstep global",
            "nodos": [{"apertura": opening, "cierreUs": close_time(opening),
                       "aperturaUs": open_time(opening)} for opening in (100, *OPENINGS)],
            "posesRealesAdicionales": 6,
            "estadosConOriginal": len(OPENINGS) + 1,
            "maximoOverlaysActivadosEnMicrosegundosEnteros": 2,
            "maximoOverlaysActivadosIncluyendoCruceOcultoDe1us": 3,
            "maximoPosesContribuyentesIncluidoOriginal": 2,
            "sinMesetasDuranteCierreOApertura": True, "sumaPesosSiempreUno": True,
            "muestras30fps": frames,
            "muestras30fpsEnCuadrosReales": actual_frames,
            "nota": "Validación matemática de mezcla/opacidad; no sustituye la revisión de PNG ni la exportación nativa."}


def config() -> dict:
    # These are the intentionally small user-adjustable inputs. The native
    # opacity keys are generated from this schedule rather than edited by hand.
    return {"version": "V8", "iniciosUs": list(BLINK_STARTS_US),
            "duracionUs": BLINK_DURATION_US, "cierreUs": CLOSE_US,
            "cerradoUs": HOLD_US, "aperturaUs": OPEN_US,
            "poses": [{"apertura": s["apertura"], "archivo": s["archivo"]} for s in states()]}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--draft", action="store_true", help="Configura y valida tiempos sin exigir los PNG")
    args = parser.parse_args()
    assert sha(SOURCE) == SOURCE_SHA256, "V6 no coincide con el archivo aprobado"
    preserved = {str(p.relative_to(ROOT)): sha(p) for p in (
        SOURCE, ROOT / "validacion-v6" / "headbop-v6.mp4",
        ROOT / "proyecto-headbop-v7" / "headbop-test2xd.royer",
        ROOT / "validacion-v7" / "headbop-v7.mp4",
        ROOT / "borradores" / "V4-aprobada" / "headbop-test2xd-v4.royer",
        ROOT / "borradores" / "V4-aprobada" / "headbop-v4.mp4",
    ) if p.exists()}
    OUTPUT.mkdir(parents=True, exist_ok=True)
    dump(OUTPUT / "ajustes-parpadeo-v8.json", config())
    timing = validate_timing()
    dump(OUTPUT / "validacion-tiempos-v8.json", timing)
    if args.draft:
        print(json.dumps({"borrador": str(OUTPUT), "validacionTiempo": True, "faltanAssets": [
            state["archivo"] for state in states() if not (ROOT / state["archivo"]).exists()]},
            ensure_ascii=False, indent=2))
        return

    new_media = {f"medios/000{index + 9:02d}.png": rgba_png_data(ROOT / state["archivo"])
                 for index, state in enumerate(states())}
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
                layer["fila"] += len(OPENINGS)
        overlays = []
        for index, state in enumerate(states()):
            layer = copy.deepcopy(head_layer)
            clip = layer["clip"]
            layer["fila"] = head_layer["fila"] + index + 1
            clip["id"] = original["siguienteId"] + index
            uri = f"medios/000{index + 9:02d}.png"
            clip["origen"]["uri"] = uri
            clip["origen"]["nombre"] = state["nombre"] + ".png"
            clip["origen"]["bytes"] = len(new_media[uri])
            keys = opacity_keys(state, head_layer["inicio"], duration)
            clip["piezas"] = [
                {"nombre": name, "region": dict(zip(("izquierda", "arriba", "derecha", "abajo"), region)),
                 "encuadre": relative_encuadre(0.0), "claves": copy.deepcopy(keys)}
                for name, region in EYE_REGIONS.items()]
            for key in head:
                if key not in {"id", "origen", "piezas"}:
                    assert clip[key] == head[key], (state["archivo"], key)
            assert layer["inicio"] == head_layer["inicio"]
            overlays.append(layer)
        project["capas"].extend(overlays)
        project["siguienteId"] += len(overlays)
        for layer, old in zip(project["capas"][:7], original["capas"]):
            check = copy.deepcopy(layer)
            check["fila"] = old["fila"]
            assert check == old, old["clip"]["id"]
        for key in original:
            if key not in {"capas", "siguienteId"}:
                assert project[key] == original[key], key
        ids = [clip["id"] for clip in project["principal"]] + [
            layer["clip"]["id"] for layer in project["capas"]] + [audio["id"] for audio in project["audios"]]
        assert len(set(ids)) == len(ids) and max(ids) < project["siguienteId"]
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
                original_media.append({"ruta": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
            for name, data in new_media.items():
                assert target_zip.read(name) == data
    for relative, expected in preserved.items():
        assert sha(ROOT / relative) == expected, relative
    original_keys = sum(len(clip.get("claves", [])) for clip in original["principal"] + [
        layer["clip"] for layer in original["capas"]])
    inherited_keys = sum(len(layer["clip"]["claves"]) for layer in overlays)
    opacity_count = sum(len(piece["claves"]) for layer in overlays for piece in layer["clip"]["piezas"])
    assert original_keys == 72 and inherited_keys == 108 and opacity_count == 308
    assert len(project["capas"]) == 16 and len(original_media) == 9
    dump(OUTPUT / "headbop-proyecto.json", project)
    dump(OUTPUT / "manifiesto-headbop-v8.json", {
        "version": "V8", "fuente": str(SOURCE.relative_to(ROOT)), "sha256Fuente": SOURCE_SHA256,
        "archivo": target.name, "sha256": sha(target), "bytes": target.stat().st_size,
        "configuracion": config(), "capas": len(project["capas"]), "capasV6Conservadas": 7,
        "nuevasCapas": [{"id": layer["clip"]["id"], "fila": layer["fila"],
                         "nombre": layer["clip"]["origen"]["nombre"], "headBopIdenticoCabeza": True,
                         "clavesMovimientoIdenticasCabeza": True, "numeroPiezasUv": 2} for layer in overlays],
        "clavesV6Conservadas": original_keys, "clavesMovimientoHeredadasOverlays": inherited_keys,
        "nuevasClavesSoloOpacidad": opacity_count, "mediosV6Conservados": original_media,
        "nuevosMedios": [{"ruta": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                         "dimensiones": [1254, 1254]} for name, data in new_media.items()],
        "versionesAnterioresIntactas": preserved, "validacionOpacidad": timing,
        "validacion": {"zipCRC": True, "mediosIdenticosV6": True,
                       "capasExistentesIdenticasExceptoFilas": True, "movimientoCabezaSinCambios": True,
                       "sinDeformacionEscala": True, "musicaIntroDuracionSalidaIdenticas": True,
                       "exportadoEnApp": False, "revisionVisualNativa": False, "pruebaFluidez": False},
        "limitaciones": "La validación de opacidad y estructura no sustituye revisar los PNG y la exportación nativa."})
    (OUTPUT / "README.md").write_text(
        "# Head bop V8 · parpadeo con más poses\n\n"
        "Conserva V6 completa: medios, música, intro, duración, encuadre, movimiento y geometría. "
        "V4 y las versiones previas siguen intactas. Añade nueve estados oculares reales sobre la cabeza: "
        "90, 80, 65, 55, 45, 30, 20, 10 y 0% abiertos, además del abierto original. "
        "Los seis nuevos dibujos se combinan con los tres de V7.\n\n"
        "Los nombres de los PNG son etiquetas de generación. La revisión del área visible "
        "de ambos iris encontró que ojos-45.png estaba más abierto que ojos-55.png. "
        "Por eso el nodo nominal 55% usa ojos-45.png y el nodo nominal 45% usa ojos-55.png, "
        "sin editar ningún raster. El orden temporal sigue la apertura verificada: original, "
        "90, 80, 65, archivo45, archivo55, 30, 20, 10 y cerrado. Los porcentajes son nombres "
        "de nodos de animación, no una medición física de apertura.\n\n"
        "Dos parpadeos a 8.150 y 12.050 s, de 0.660 s: cierre de 0.240 s, cerrado de 0.100 s "
        "y apertura de 0.320 s. Los tiempos de cada pose se obtienen invirtiendo "
        "smoothstep 3u²−2u³ según su apertura real. El gesto acelera y frena globalmente. "
        "Entre poses se usa opacidad lineal para avanzar sin las pausas intermedias de V7. "
        "A 30 fps cambia la mezcla en cada cuadro de cierre/apertura.\n\n"
        "Sólo dos poses contribuyen a la imagen a la vez. Los estados inferiores ya tapados se "
        "desactivan mediante claves de 1 µs mientras una capa opaca los cubre; se reactivan ocultos "
        "antes de reaparecer. Como máximo dos overlays están activos en instantes enteros de µs "
        "y tres durante el cruce oculto de 1 µs. Esto limita el apilado; no es una medición de rendimiento.\n\n"
        "Cada capa ocular copia los tiempos, el encuadre, las doce claves y HeadBop de Cabeza. "
        "Sólo las dos piezas UV de los ojos cambian opacidad; no se escala ni deforma la cabeza. "
        "Las 72 claves V6 y los nueve medios originales se verifican intactos.\n\n"
        "Regenerar con `python crear_headbop-v8.py`; `--draft` valida tiempos y escribe ajustes sin "
        "exigir los PNG. `ajustes-parpadeo-v8.json` reúne inicios, duración y poses. "
        "La validación matemática se guarda en `validacion-tiempos-v8.json`. "
        "Pendiente en este generador: revisión de los dibujos y exportación nativa. "
        "No se ha medido fluidez.\n", encoding="utf-8")
    print(json.dumps({"proyectoV8": str(target), "sha256": sha(target), "capas": len(project["capas"]),
                      "mediosV6Identicos": len(original_media), "clavesV6Intactas": original_keys,
                      "clavesOpacidad": opacity_count, "parpadeos": list(BLINK_STARTS_US),
                      "versionesAnterioresIntactas": True}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
