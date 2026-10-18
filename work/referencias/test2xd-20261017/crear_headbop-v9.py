"""Alinea sólo la fotografía inicial con la cara del rig V8, sin editar medios."""
from pathlib import Path
import copy
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / 'proyecto-headbop-v8/headbop-test2xd.royer'
SOURCE_SHA = '3356ecf7112343bf8dd2a9990811044dd3339197fb766c78322914b9d5cb3a5b'
OUT = ROOT / 'proyecto-headbop-v9'
ALIGN = dict(escala=0.835825001867422, x=-0.000048777190598954165,
             y=0.02717746612163515, rotacion=0.06278970587806137)

def sha(data):
    return hashlib.sha256(data).hexdigest()

def save(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

def main():
    assert sha(SOURCE.read_bytes()) == SOURCE_SHA
    OUT.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(SOURCE) as src:
        assert src.testzip() is None
        original = json.loads(src.read('proyecto.json'))
        project = copy.deepcopy(original)
        photo = next(l['clip'] for l in project['capas'] if l['clip']['id'] == 2)
        before = copy.deepcopy(photo)
        assert photo['hasta'] == 1_100_000 and len(photo['claves']) == 3
        photo['encuadre'].update(ALIGN)
        for key in photo['claves']:
            key['encuadre'].update(ALIGN)
        # Restaurar sólo los cuatro parámetros en sus cuatro ubicaciones
        # debe producir de nuevo el manifiesto V8 entero, sin otros cambios.
        restored = copy.deepcopy(project)
        restored_photo = next(l['clip'] for l in restored['capas'] if l['clip']['id'] == 2)
        for field in ALIGN:
            restored_photo['encuadre'][field] = before['encuadre'][field]
            for current_key, old_key in zip(restored_photo['claves'], before['claves']):
                current_key['encuadre'][field] = old_key['encuadre'][field]
        assert restored == original
        assert [k['t'] for k in photo['claves']] == [0, 650_000, 1_100_000]
        assert [k['encuadre']['opacidad'] for k in photo['claves']] == [1.0, 1.0, 0.0]
        target = OUT / 'headbop-test2xd.royer'
        temp = target.with_suffix('.royer.part')
        with zipfile.ZipFile(temp, 'w') as dst:
            for info in src.infolist():
                data = (json.dumps(project, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
                        if info.filename == 'proyecto.json' else src.read(info.filename))
                dst.writestr(copy.copy(info), data)
        temp.replace(target)
        media = []
        with zipfile.ZipFile(target) as dst:
            assert dst.testzip() is None
            assert dst.namelist() == src.namelist()
            assert json.loads(dst.read('proyecto.json')) == project
            for name in src.namelist():
                if name == 'proyecto.json':
                    continue
                data = src.read(name)
                assert dst.read(name) == data
                media.append(dict(ruta=name, sha256=sha(data), bytes=len(data)))
        assert len(media) == 18
    assert sha(SOURCE.read_bytes()) == SOURCE_SHA
    save('headbop-proyecto.json', project)
    save('ajustes-intro-v9.json', dict(fotoId=2, encuadre=ALIGN,
         aplicarIgualEnTresClaves=True, tiemposConservados=[0,650_000,1_100_000],
         metodo='Similitud facial estática, medida sobre30rasgos a1080; sin deformar ni editar bitmaps',
         diagnostico='analisis/registro-cara-intro-v9.json'))
    save('manifiesto-headbop-v9.json', dict(version='V9', fuente=str(SOURCE),
         sha256Fuente=SOURCE_SHA, sha256=sha(target.read_bytes()), mediosIdenticos=media,
         clipModificado=2, camposModificados=list(ALIGN), ubicaciones=4, totalCambios=16,
         encuadreAntes=before['encuadre'], encuadreDespues=photo['encuadre'],
         clavesSinAnadirNiQuitar=True, tiemposOpacidadCurvasConservados=True,
         rigCuelloParpadeosMusicaSinCambios=True, desdeUsSinCambios=1_100_000,
         validacionNativa='pendiente', comparacionCaraNativa='pendiente'))
    (OUT/'README.md').write_text(
        '# Head bop V9 · encaje de la cara en el fundido inicial\n\n'
        'Sólo cambia el encuadre de la fotografía original id2 y los mismos cuatro '
        'parámetros en sus tres claves: traslación, escala uniforme y giro estáticos. '
        'Se conservan tiempos, curvas y opacidad. No se modifica la cabeza del rig '
        'ni el cuello, el cabello, el baile o los parpadeos V8. Los18medios permanecen '
        'idénticos y no se añaden claves.\n\n'
        'Ajuste calculado por correspondencias faciales, con diagnóstico independiente '
        'en analisis/registro-cara-intro-v9.json. No implica que el diseño completo '
        'de pelo y ropa coincida. La exportación y la medición de los fotogramas '
        'nativos siguen pendientes al preparar este paquete.\n\n'
        'Regenerar: python crear_headbop-v9.py. Ajustes en ajustes-intro-v9.json.\n', encoding='utf-8')
    print(json.dumps(dict(paquete=str(target), sha256=sha(target.read_bytes()),
                         mediosIdenticos=len(media), cambios=16), ensure_ascii=False))

if __name__ == '__main__':
    main()
