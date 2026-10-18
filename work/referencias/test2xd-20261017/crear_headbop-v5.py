"""Preserva V4 y prepara V5 sin regenerar geometría, intro, claves ni medios.

Ejecutar con Python estándar. No necesita Pillow, FFmpeg ni el motor Android.
La exportación y la valoración visual deben realizarse después en Royer Method.
"""
from __future__ import annotations

import copy
import hashlib
import json
import shutil
import zipfile
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "proyecto-headbop-v4" / "headbop-test2xd.royer"
SOURCE_SHA256 = "4a62aaf97290cc4133e5e3c3dc61e035577fe939eb3ebc4eb482289941900836"
OUTPUT = ROOT / "proyecto-headbop-v5"
ARCHIVE = ROOT / "borradores" / "V4-aprobada"
PATCH = {"naturalidad": 0.9, "giro": 4.8, "rebote": 0.010}


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path: Path, value) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def preserve_exact(source: Path, target: Path) -> dict:
    """Nunca reemplaza una copia archivada con contenido distinto."""
    if target.exists():
        if sha(source) != sha(target):
            raise RuntimeError(f"Borrador existente distinto: {target}; se conserva sin sobrescribir")
    else:
        shutil.copy2(source, target)
    return {"origen": str(source.relative_to(ROOT)), "copia": target.name,
            "bytes": target.stat().st_size, "sha256": sha(target)}


def differences(a, b, path="") -> list[str]:
    if type(a) is not type(b):
        return [path]
    if isinstance(a, dict):
        result = []
        for key in sorted(a.keys() | b.keys()):
            p = f"{path}.{key}" if path else key
            result.extend([p] if key not in a or key not in b else differences(a[key], b[key], p))
        return result
    if isinstance(a, list):
        if len(a) != len(b):
            return [path]
        return [p for i, (x, y) in enumerate(zip(a, b)) for p in differences(x, y, f"{path}[{i}]")]
    return [] if a == b else [path]


def count_keys(project: dict) -> int:
    clips = project["principal"] + [layer["clip"] for layer in project["capas"]]
    return sum(len(clip.get("claves", [])) for clip in clips)


def main() -> None:
    if sha(SOURCE) != SOURCE_SHA256:
        raise RuntimeError("V4 no coincide con el proyecto aprobado esperado; no se modifica nada")
    ARCHIVE.mkdir(parents=True, exist_ok=True)
    saved = [
        preserve_exact(SOURCE, ARCHIVE / "headbop-test2xd-v4.royer"),
        preserve_exact(ROOT / "validacion-v4" / "headbop-v4.mp4", ARCHIVE / "headbop-v4.mp4"),
        preserve_exact(SOURCE.parent / "controles-headbop.json", ARCHIVE / "controles-headbop-v4.json"),
        preserve_exact(SOURCE.parent / "ajustes-headbop-v4.json", ARCHIVE / "ajustes-headbop-v4.json"),
        preserve_exact(SOURCE.parent / "README.md", ARCHIVE / "README-origen-V4.md"),
        preserve_exact(SOURCE.parent / "headbop-proyecto.json", ARCHIVE / "headbop-proyecto-v4.json"),
    ]
    archive_manifest = ARCHIVE / "manifiesto-borrador.json"
    if not archive_manifest.exists():
        write_json(archive_manifest, {"version": "V4", "estado": "Borrador guardado por petición del usuario",
            "creadoUTC": datetime.now(timezone.utc).isoformat(), "archivos": saved})
    archive_readme = ARCHIVE / "README.md"
    if not archive_readme.exists():
        archive_readme.write_text(
            "# Borrador V4 aprobado\n\n"
            "Se guarda la versión que gustó al usuario antes de modificar el movimiento de la cabeza. "
            "El proyecto, el video, los controles y el README de origen son copias exactas. "
            "Los hashes de integridad están en manifiesto-borrador.json.\n\n"
            "La nueva prueba V5 está en otro directorio y no reemplaza este borrador. "
            "Esta aprobación de dirección visual no equivale a una medición de coincidencia con la referencia "
            "ni a una prueba de fluidez.\n", encoding="utf-8")

    with zipfile.ZipFile(SOURCE) as source_zip:
        assert source_zip.testzip() is None
        original = json.loads(source_zip.read("proyecto.json"))
        project = copy.deepcopy(original)
        changed = []
        for i, layer in enumerate(project["capas"]):
            head = layer["clip"].get("headBop")
            if head is None:
                continue
            old = copy.deepcopy(head)
            head.update(PATCH)
            changed.append({"capa": i, "id": layer["clip"]["id"],
                "nombre": layer["clip"]["origen"]["nombre"],
                "campos": {k: {"antes": old.get(k, 0.0 if k == "naturalidad" else None),
                                "despues": v} for k, v in PATCH.items()}})
        assert len(changed) == 6
        expected_changes = sorted(f"capas[{x['capa']}].clip.headBop.{k}" for x in changed for k in PATCH)
        actual_changes = differences(original, project)
        assert sorted(actual_changes) == expected_changes, actual_changes
        assert count_keys(project) == count_keys(original) == 72
        assert len(project["capas"]) == 7
        for layer in project["capas"]:
            head = layer["clip"].get("headBop")
            if head and head["rol"] in ("CABEZA", "CABELLO"):
                assert head["intensidad"] == 1.0

        OUTPUT.mkdir(parents=True, exist_ok=True)
        target = OUTPUT / "headbop-test2xd.royer"
        temp = target.with_suffix(".royer.part")
        with zipfile.ZipFile(temp, "w") as target_zip:
            for entry in source_zip.infolist():
                data = (json.dumps(project, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
                        if entry.filename == "proyecto.json" else source_zip.read(entry.filename))
                # ZipFile.writestr actualiza header_offset: no mutar el índice del ZIP fuente.
                target_zip.writestr(copy.copy(entry), data)
        temp.replace(target)

        media = []
        with zipfile.ZipFile(target) as target_zip:
            assert target_zip.testzip() is None
            assert source_zip.namelist() == target_zip.namelist()
            assert json.loads(target_zip.read("proyecto.json")) == project
            for name in source_zip.namelist():
                if name == "proyecto.json":
                    continue
                old = source_zip.read(name)
                new = target_zip.read(name)
                assert new == old, name
                media.append({"ruta": name, "bytes": len(new),
                              "sha256": hashlib.sha256(new).hexdigest(), "identicoV4": True})

    write_json(OUTPUT / "headbop-proyecto.json", project)
    controls = json.loads((SOURCE.parent / "controles-headbop.json").read_text(encoding="utf-8"))
    controls.update(PATCH)
    write_json(OUTPUT / "controles-headbop.json", controls)
    write_json(OUTPUT / "ajustes-headbop-v5.json", {"fuente": str(SOURCE.relative_to(ROOT)),
        "sha256Fuente": SOURCE_SHA256, "headBopComun": PATCH,
        "conservar": ["medios", "intro", "claves", "encuadres", "pivotes", "musica", "tiempos", "intensidades"]})
    manifest = {"version": "V5", "origen": str(SOURCE.relative_to(ROOT)),
        "sha256Origen": SOURCE_SHA256, "archivo": target.name, "bytes": target.stat().st_size,
        "sha256": sha(target), "headBopModificados": changed,
        "diferenciasJSON": actual_changes, "clavesV4": 72, "clavesV5": 72,
        "medios": media, "borradorV4": str(ARCHIVE.relative_to(ROOT)),
        "validacion": {"zipCRC": True, "mediosIdenticos": True, "clavesIdenticas": True,
            "unicosCambiosSonTresCamposHeadBop": True,
            "exportadoEnApp": False, "validacionVisualNativa": False, "pruebaFluidez": False}}
    write_json(OUTPUT / "manifiesto-headbop-v5.json", manifest)
    (OUTPUT / "README.md").write_text(
        "# Head bop V5: prueba de movimiento natural\n\n"
        "La V4 que gustó al usuario está conservada sin cambios en ../borradores/V4-aprobada. "
        "V5 parte de su paquete final, sin volver a construir la intro ni remapear los PNG.\n\n"
        "En las seis instancias Head bop se cambia únicamente naturalidad de 0 (predeterminado anterior) "
        "a 0.9, giro de 7 a 4.8 grados y rebote de 0.012 a 0.010. "
        "Las intensidades de cabeza y ambos cabellos siguen en 1, sus movimientos comparten la misma base "
        "y conservan BPM, fase, tiempos, pivotes, cuello, retardo y arrastre. "
        "La nueva propiedad necesita una versión de Royer Method que implemente naturalidad; "
        "una versión anterior la ignorará.\n\n"
        "Se conservan los nueve medios byte por byte, siete capas y las 72 claves originales "
        "(intro y fundido; cero claves de baile). No se añade texto ni +. "
        "El manifiesto documenta hashes y los 18 únicos cambios del JSON.\n\n"
        "Regenerar desde la carpeta raíz con `python crear_headbop-v5.py`. "
        "El script rechaza una V4 de origen con hash distinto y nunca sustituye un borrador archivado diferente.\n\n"
        "Validado aquí: estructura ZIP, CRC, integridad de medios, claves y alcance de cambios. "
        "Pendiente al preparar este paquete: exportación en la app, revisión visual del movimiento "
        "y cualquier comprobación de fluidez. No se afirma coincidencia con una referencia.\n", encoding="utf-8")
    print(json.dumps({"borradorV4": str(ARCHIVE), "proyectoV5": str(target), "sha256": sha(target),
                      "mediosIdenticos": len(media), "claves": 72, "cambiosJSON": len(actual_changes)},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
