"""V6 desde V4: naturalidad sólo en cabeza/cabello, conservando amplitud V4.

No modifica V4, V5 ni sus borradores y no regenera ningún medio o geometría.
Necesita Python estándar. La revisión visual requiere exportar con el motor V6.
"""
from __future__ import annotations

import copy
import hashlib
import json
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "proyecto-headbop-v4" / "headbop-test2xd.royer"
SOURCE_SHA256 = "4a62aaf97290cc4133e5e3c3dc61e035577fe939eb3ebc4eb482289941900836"
OUTPUT = ROOT / "proyecto-headbop-v6"
NATURALIDAD = 0.8


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def dump(path: Path, data) -> None:
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


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


def main() -> None:
    if sha(SOURCE) != SOURCE_SHA256:
        raise RuntimeError("La V4 de origen no coincide con el hash esperado")
    preserved = {}
    for relative in (
        "proyecto-headbop-v4/headbop-test2xd.royer",
        "validacion-v4/headbop-v4.mp4",
        "borradores/V4-aprobada/headbop-test2xd-v4.royer",
        "borradores/V4-aprobada/headbop-v4.mp4",
        "proyecto-headbop-v5/headbop-test2xd.royer",
    ):
        p = ROOT / relative
        if p.exists():
            preserved[relative] = sha(p)

    with zipfile.ZipFile(SOURCE) as source_zip:
        assert source_zip.testzip() is None
        original = json.loads(source_zip.read("proyecto.json"))
        project = copy.deepcopy(original)
        changes = []
        natural = []
        inherited = []
        for i, layer in enumerate(project["capas"]):
            clip = layer["clip"]
            head = clip.get("headBop")
            if head is None:
                continue
            assert head["giro"] == 7.0 and head["rebote"] == 0.012
            assert "naturalidad" not in head
            record = {"id": clip["id"], "nombre": clip["origen"]["nombre"], "rol": head["rol"]}
            if head["rol"] in ("CABEZA", "CABELLO"):
                assert head["intensidad"] == 1.0
                head["naturalidad"] = NATURALIDAD
                changes.append(f"capas[{i}].clip.headBop.naturalidad")
                natural.append({**record, "naturalidad": NATURALIDAD})
            else:
                inherited.append({**record, "naturalidadEfectiva": 0.0,
                                  "campoAusenteComoV4": True})
        assert len(natural) == 3 and len(inherited) == 3
        actual = differences(original, project)
        assert sorted(changes) == sorted(actual), actual
        all_clips = project["principal"] + [x["clip"] for x in project["capas"]]
        key_count = sum(len(x.get("claves", [])) for x in all_clips)
        assert key_count == 72
        assert len(project["capas"]) == 7

        OUTPUT.mkdir(parents=True, exist_ok=True)
        target = OUTPUT / "headbop-test2xd.royer"
        temp = target.with_suffix(".royer.part")
        with zipfile.ZipFile(temp, "w") as target_zip:
            for entry in source_zip.infolist():
                data = (json.dumps(project, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
                        if entry.filename == "proyecto.json" else source_zip.read(entry.filename))
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
                data = target_zip.read(name)
                assert data == source_zip.read(name), name
                media.append({"ruta": name, "bytes": len(data),
                              "sha256": hashlib.sha256(data).hexdigest(), "identicoV4": True})
    assert len(media) == 9
    for relative, expected in preserved.items():
        assert sha(ROOT / relative) == expected, relative
    assert sha(SOURCE) == SOURCE_SHA256

    dump(OUTPUT / "headbop-proyecto.json", project)
    controls = json.loads((SOURCE.parent / "controles-headbop.json").read_text(encoding="utf-8"))
    for name in ("cabeza.png", "cabello-trasero.png", "flequillo.png"):
        controls["piezas"][name]["naturalidad"] = NATURALIDAD
    dump(OUTPUT / "controles-headbop.json", controls)
    dump(OUTPUT / "ajustes-headbop-v6.json", {
        "fuente": str(SOURCE.relative_to(ROOT)), "sha256Fuente": SOURCE_SHA256,
        "naturalidadPorRol": {"CABEZA": NATURALIDAD, "CABELLO": NATURALIDAD},
        "otrosRoles": "Conservar campo ausente y naturalidad efectiva 0 como V4",
        "giroConservado": 7.0, "reboteConservado": 0.012,
        "motorRequerido": "V6, naturalidad con rotación/traslación y sin deformación de escala"})
    manifest = {"version": "V6", "origen": str(SOURCE.relative_to(ROOT)),
        "sha256Origen": SOURCE_SHA256, "archivo": target.name,
        "bytes": target.stat().st_size, "sha256": sha(target),
        "headBopNatural": natural, "headBopConservadosV4": inherited,
        "diferenciasJSON": actual, "numeroCambios": len(actual),
        "giroTodasCapas": 7.0, "reboteTodasCapas": 0.012,
        "claves": key_count, "medios": media, "archivosPreviosSinModificar": preserved,
        "validacion": {"zipCRC": True, "mediosIdenticosV4": True,
            "clavesIdenticasV4": True, "unicosCambiosTresNaturalidad": True,
            "introPivotesMusicaTiemposIdenticosV4": True,
            "exportadoEnApp": False, "validacionVisualNativa": False, "pruebaFluidez": False}}
    dump(OUTPUT / "manifiesto-headbop-v6.json", manifest)
    (OUTPUT / "README.md").write_text(
        "# Head bop V6: movimiento natural sin reducir amplitud\n\n"
        "Parte directamente del paquete V4 aprobado, no del V5. V4, su borrador y V5 se conservan intactos. "
        "En el JSON del proyecto sólo se añade naturalidad=0.8 a cabeza y ambos cabellos: tres campos en total. "
        "Cuerpo y manos conservan el campo ausente (naturalidad efectiva 0) y su movimiento V4. "
        "Todas las instancias mantienen giro=7 grados y rebote=0.012, además de sus intensidades, "
        "BPM, fase, cuello, pivotes, retardo, arrastre y tiempos originales.\n\n"
        "Los nueve medios son idénticos byte por byte a V4; se conservan siete capas, "
        "72 claves de intro/fundido y cero claves de baile. La intro, la música, el encuadre y "
        "la ausencia de textos/+ se mantienen sin regenerar geometría. El manifiesto recoge hashes y deltas exactos.\n\n"
        "Requiere compilar e instalar el motor V6 que implementa naturalidad usando solamente "
        "rotación/traslación, sin aplanar ni estirar la cabeza y sin atenuar la amplitud. "
        "Abrir este archivo en el motor V5 no comprueba esa corrección.\n\n"
        "Regenerar desde la carpeta raíz: `python crear_headbop-v6.py`. "
        "El script usa el ZIP V4 como fuente y rechaza un hash distinto. "
        "No vuelve a generar PNG, intro ni claves.\n\n"
        "Validado: CRC, medios, claves y los tres únicos cambios del JSON. "
        "Pendiente: exportación desde la app con motor V6 y revisión visual del movimiento. "
        "No se afirma que ya exista un render ni una comprobación de fluidez.\n", encoding="utf-8")
    print(json.dumps({"proyectoV6": str(target), "sha256": sha(target),
        "mediosIdenticos": len(media), "clavesIdenticas": key_count,
        "cambiosJSON": actual, "versionesAnterioresIntactas": True}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
