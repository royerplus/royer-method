"""Proyecto editable de head bop para Royer Method; NO renderiza animación fuera de la app.

Python3 +Pillow. --draft escribe el manifiesto provisional aunque falten los seis PNG.
La versión estricta valida el rig RGBA alineado y empaqueta los originales en .royer.
"""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
import math
from pathlib import Path
import sys
import zipfile

try:
    from PIL import Image
except ImportError:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'python'))
    from PIL import Image

BASE = Path(__file__).resolve().parent
FIN_US = 15_733_333
INICIO_FINAL_US = 6_840_000
BPM = 107.6646164379024
ROL_ARCHIVOS = [
    ('cabello-trasero.png', 'CABELLO', 'Cabello trasero', (-.13, -.07, -5)),
    ('brazo-elevado.png', 'MANO_DERECHA', 'Mano elevada', (.12, -.02, 8)),
    ('cuerpo.png', 'CUERPO', 'Cuerpo', (0, .07, 0)),
    ('cabeza.png', 'CABEZA', 'Cabeza', (0, -.13, 0)),
    ('flequillo.png', 'CABELLO', 'Flequillo', (.10, -.12, 5)),
    ('brazo-frontal.png', 'MANO_IZQUIERDA', 'Mano frontal', (-.10, .08, -7)),
]
DEFAULT = {
    'cuelloX': .55, 'cuelloY': .62, 'giro': 7.0, 'rebote': .012,
    'zoomFinal': 1.14, 'desplazamientoFinalX': 0.0, 'desplazamientoFinalY': .020,
    'fundidoFinalUs': 200000,
    'piezas': {
        'cabello-trasero.png': {'pivoteX': .51, 'pivoteY': .23, 'retardo': 95000, 'intensidad': 1.0, 'arrastre': 1.0},
        'cuerpo.png': {'pivoteX': .50, 'pivoteY': .88, 'retardo': 0, 'intensidad': .40},
        'cabeza.png': {'pivoteX': .55, 'pivoteY': .62, 'retardo': 0, 'intensidad': 1.0},
        'flequillo.png': {'pivoteX': .49, 'pivoteY': .18, 'retardo': 65000, 'intensidad': 1.0, 'arrastre': .70},
        'brazo-elevado.png': {'pivoteX': .78, 'pivoteY': .58, 'retardo': 45000, 'intensidad': .75},
        'brazo-frontal.png': {'pivoteX': .34, 'pivoteY': .76, 'retardo': 70000, 'intensidad': .80},
    }
}


def enc(escala=1., x=0., y=0., giro=0., opacidad=1.):
    return dict(ajuste='ENCAJAR', escala=escala, x=x, y=y, rotacion=giro,
                opacidad=opacidad, espejo=False, recorte=[0., 0., 0., 0.])


def clave(t, pose, curva='SUAVE'):
    return {'t': int(t), 'encuadre': pose, 'curva': curva}


def argparser():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--assets', type=Path, default=BASE/'assets-v4')
    p.add_argument('--out', type=Path, default=BASE/'proyecto-headbop-v4')
    p.add_argument('--original', type=Path, default=BASE/'entrada/Screenshot_20260623-190945.jpg')
    p.add_argument('--musica', type=Path, default=BASE/'analisis/musica-original.m4a')
    p.add_argument('--config', type=Path, help='JSON de ajustes; combina claves globales y piezas por nombre de PNG')
    p.add_argument('--base-config', type=Path, default=BASE/'proyecto-headbop-v3/controles-headbop.json', help='Punto de partida V3; --config sobrescribe sólo los ajustes indicados')
    p.add_argument('--registro', type=Path, default=BASE/'registro-assets-v3.json', help='Encuadres y pivotes medidos; se aplica antes de las configuraciones')
    p.add_argument('--sin-textos', action='store_true', default=True, help='V4 predeterminado: sin capas de texto ni signo +')
    p.add_argument('--con-textos', dest='sin_textos', action='store_false', help='Restaurar los rótulos explicativos de la intro anterior')
    p.add_argument('--sin-credito', action='store_true', default=True, help='V4 predeterminado: sin capa de crédito RAGI; atribución en README y fotografía original intacta')
    p.add_argument('--con-credito', dest='sin_credito', action='store_false', help='Restaurar la capa de crédito RAGI de las versiones anteriores')
    p.add_argument('--escala', type=float, default=.84)
    p.add_argument('--bpm', type=float, default=BPM)
    p.add_argument('--inicio-final', type=float, default=INICIO_FINAL_US/1e6)
    p.add_argument('--duracion', type=float, default=FIN_US/1e6)
    p.add_argument('--draft', action='store_true', help='Permite faltantes; no escribe paquete incompleto')
    return p


def validar_png(path):
    with Image.open(path) as im:
        if im.format != 'PNG' or im.mode != 'RGBA':
            raise ValueError(f'{path.name}: se necesita PNG RGBA real, recibido {im.format}/{im.mode}')
        alpha = im.getchannel('A')
        hist = alpha.histogram()
        pixeles = im.width*im.height
        transparentes = sum(hist[:255])/pixeles
        visibles = sum(hist[8:])/pixeles
        if transparentes < .01 or visibles < .003:
            raise ValueError(f'{path.name}: alpha inválido; transparente={transparentes:.4f}, visible={visibles:.4f}')
        return {'ancho': im.width, 'alto': im.height, 'modo': im.mode,
                'fraccion_no_opaca': transparentes, 'fraccion_visible': visibles,
                'caja_alpha': alpha.getbbox()}


def combinar_config(conf, custom):
    for k, v in custom.items():
        if k == 'piezas':
            for name, values in v.items():
                conf['piezas'].setdefault(name, {}).update(copy.deepcopy(values))
        else:
            conf[k] = copy.deepcopy(v)


def adaptar_geometria(conf, stats, registro):
    """Conserva la transformación de una pieza al cambiar el tamaño de su PNG.

    marcoReferenciaUV indica dónde queda el lienzo anterior dentro del nuevo,
    por ejemplo [0, 0, 1, 2/3] al prolongar un cuadrado hacia abajo a 2:3.
    No se supone que un PNG alargado conserve la composición sin ese dato.
    """
    assets_ref = json.loads(registro.read_text(encoding='utf-8-sig')).get('assets', {}) if registro else {}
    resultados = {}
    for name, stat in stats.items():
        c = conf['piezas'][name]
        wn, hn = stat['ancho'], stat['alto']
        anterior = c.get('geometriaAplicada')
        if anterior and anterior.get('tamanoPNG') == [wn, hn]:
            resultados[name] = anterior
            continue
        wr, hr = c.get('tamanoReferencia', assets_ref.get(name, {}).get('size', [1254, 1254]))
        marco = c.get('marcoReferenciaUV', [0., 0., 1., 1.])
        l, t, r, b = map(float, marco)
        if not (r > l and b > t and wr > 0 and hr > 0):
            raise ValueError(f'{name}: marcoReferenciaUV/tamanoReferencia inválidos')
        sx, sy = (r-l)*wn/wr, (b-t)*hn/hr
        if not math.isclose(sx, sy, rel_tol=.002):
            raise ValueError(f'{name}: la relación del lienzo cambió. Indica marcoReferenciaUV '
                             'para conservar la proporción original sin deformarla.')
        e = c.setdefault('encuadre', {})
        escala = float(e.get('escala', .84))*max(wn, hn)/max(wr, hr)/sx
        giro = math.radians(float(e.get('rotacion', 0.)))
        dx = ((l+r)/2-.5)*wn/max(wn, hn)*escala
        dy = ((t+b)/2-.5)*hn/max(wn, hn)*escala
        e['escala'] = escala
        e['x'] = float(e.get('x', 0.))-math.cos(giro)*dx+math.sin(giro)*dy
        e['y'] = float(e.get('y', 0.))-math.sin(giro)*dx-math.cos(giro)*dy
        for x, y in [('pivoteX', 'pivoteY'), ('cuelloX', 'cuelloY')]:
            if x in c and y in c:
                c[x], c[y] = l+(r-l)*float(c[x]), t+(b-t)*float(c[y])
        detalle = {'tamanoReferencia': [wr, hr], 'tamanoPNG': [wn, hn],
                   'marcoReferenciaUV': marco, 'ajuste': 'ENCAJAR', 'sinDeformacion': True}
        c['geometriaAplicada'] = detalle
        c.pop('marcoReferenciaUV', None)
        c.pop('tamanoReferencia', None)
        resultados[name] = detalle
    return resultados


def integrar_registro(conf, path):
    registro = json.loads(path.read_text(encoding='utf-8-sig'))
    assets = registro.get('assets', {})
    if registro.get('original', {}).get('neck_uv'):
        conf['cuelloX'], conf['cuelloY'] = registro['original']['neck_uv']
    if registro.get('neck_scene_uv'):
        conf['cuelloEscenaX'], conf['cuelloEscenaY'] = registro['neck_scene_uv']
    if not isinstance(assets, dict):
        raise ValueError('registro-assets.json debe contener assets por nombre de archivo')
    def xy(valor):
        if isinstance(valor, (list, tuple)) and len(valor) == 2:
            return float(valor[0]), float(valor[1])
        if isinstance(valor, dict):
            return float(valor.get('pivoteX', valor.get('x'))), float(valor.get('pivoteY', valor.get('y')))
        raise ValueError(f'Pivote UV inválido: {valor}')
    for name, rol, _, _ in ROL_ARCHIVOS:
        if name not in assets:
            raise ValueError(f'Falta encuadre registrado de {name}')
        a = assets[name]
        c = conf['piezas'][name]
        c['encuadre'] = a['encuadre']
        if a.get('cuelloUV') is not None:
            c['cuelloX'], c['cuelloY'] = xy(a['cuelloUV'])
        pivote = a.get('manoUV') if rol.startswith('MANO_') else a.get('raizUV')
        if pivote is not None:
            c['pivoteX'], c['pivoteY'] = xy(pivote)
        elif rol == 'CABEZA' and a.get('cuelloUV') is not None:
            c['pivoteX'], c['pivoteY'] = xy(a['cuelloUV'])


def build(args):
    if not 0 < args.escala <= 1.5 or not 20 <= args.bpm <= 300:
        raise ValueError('Escala/BPM fuera de rango')
    fin = round(args.duracion*1e6)
    inicio_final = round(args.inicio_final*1e6)
    if not 6_500_000 <= inicio_final < fin <= 19_800_000:
        raise ValueError('La intro necesita al menos6,5s y la música debe terminar antes del outro')
    conf = copy.deepcopy(DEFAULT)
    if args.registro:
        integrar_registro(conf, args.registro)
    if args.base_config:
        combinar_config(conf, json.loads(args.base_config.read_text(encoding='utf-8-sig')))
    if args.config:
        combinar_config(conf, json.loads(args.config.read_text(encoding='utf-8-sig')))
    zoom_final = float(conf.get('zoomFinal', 1.14))
    if not .5 <= zoom_final <= 2.0:
        raise ValueError('zoomFinal debe estar entre0.5 y2.0')
    delta_x = float(conf.get('desplazamientoFinalX', 0.0))
    delta_y = float(conf.get('desplazamientoFinalY', .020))
    fundido_final = max(0, min(int(conf.get('fundidoFinalUs', 200000)), (fin-inicio_final)//2))
    args.out.mkdir(parents=True, exist_ok=True)
    stats = {}
    dims = set()
    missing = []
    for name, _, _, _ in ROL_ARCHIVOS:
        path = args.assets/name
        if not path.is_file():
            missing.append(str(path))
            continue
        stats[name] = validar_png(path)
        dims.add((stats[name]['ancho'], stats[name]['alto']))
    if missing and not args.draft:
        raise FileNotFoundError('Faltan piezas:\n'+'\n'.join(missing))
    for required in (args.original, args.musica):
        if not required.is_file():
            missing.append(str(required))
    if missing and not args.draft:
        raise FileNotFoundError('Faltan archivos:\n'+'\n'.join(missing))
    geometria = adaptar_geometria(conf, stats, args.registro)
    canvas = (1080, 1080)
    fondo = args.out/'fondo-marfil.png'
    Image.new('RGB', (64, 64), (250, 247, 244)).save(fondo)
    medios = []
    registrados = {}

    def origen(path, tipo='IMAGEN', nombre=None, duracion=0):
        path = path.resolve()
        if path in registrados:
            return copy.deepcopy(registrados[path])
        ruta = f'medios/{len(medios):05d}{path.suffix.lower()}'
        size = (0, 0)
        if tipo == 'IMAGEN':
            if path.is_file():
                with Image.open(path) as im:
                    size = im.size
            else:
                size = canvas
        obj = {'uri': ruta, 'nombre': nombre or path.name, 'tipo': tipo,
               'ancho': size[0], 'alto': size[1], 'duracion': duracion,
               'fps': 0., 'conAudio': tipo == 'AUDIO', 'giro': 0,
               'bytes': path.stat().st_size if path.is_file() else 0}
        medios.append((ruta, path))
        registrados[path] = obj
        return copy.deepcopy(obj)

    contador = 0

    def clip(duracion, source=None, texto=None, pose=None, claves=None, head_bop=None):
        nonlocal contador
        contador += 1
        obj = {'id': contador, 'desde': 0, 'hasta': int(duracion), 'velocidad': 1.,
               'volumen': 0., 'mantenerTono': True, 'fundidoEntrada': 0,
               'fundidoSalida': 0, 'color': [0.]*10, 'filtro': 'NINGUNO',
               'intensidadFiltro': 1., 'encuadre': pose or enc(), 'claves': claves or [],
               'piezas': [], 'entrada': [], 'salida': [], 'duracionEntrada': 150000,
               'duracionSalida': 150000, 'continua': [], 'efectos': [],
               'transicion': 'NINGUNA', 'duracionTransicion': 0,
               'ajusteTransicion': {'curva': 'SUAVE', 'angulo': 180., 'color': -1, 'intensidad': 1.},
               'fluido': False, 'calidad': 1.}
        if source:
            obj['origen'] = source
        if texto:
            obj['texto'] = texto
        if head_bop:
            obj['headBop'] = head_bop
        return obj

    project = {'version': 1, 'formato': 'CUADRADO', 'fondo': 'BLANCO',
               'salida': {'ladoCorto': 1080, 'calidad': 'ALTA', 'hevc': False,
                          'desenfoque': True, 'muestras': 4},
               'principal': [clip(fin, origen(fondo, nombre='Fondo marfil.png'))],
               'capas': [], 'audios': [], 'siguienteId': 0}
    registro_capas = []

    def layer(c, inicio, fila, label):
        project['capas'].append({'inicio': inicio, 'fila': fila, 'mezcla': 'NORMAL', 'clip': c})
        registro_capas.append({'id': c['id'], 'nombre': label, 'inicio_us': inicio,
                               'fin_us': inicio+c['hasta'], 'fila': fila,
                               'claves': len(c['claves']), 'headBop': c.get('headBop')})

    # Foto original arriba de las piezas: se funde y descubre el rig alineado.
    original = origen(args.original, nombre='Foto original de RAGI.jpg')
    foto = clip(1_100_000, source=original, pose=enc(args.escala), claves=[
        clave(0, enc(args.escala)), clave(650_000, enc(args.escala)),
        clave(1_100_000, enc(args.escala, opacidad=0.))])
    layer(foto, 0, 9, 'Foto original → rig')

    # Sólo las poses del despiece llevan claves; el baile usa HeadBop procedural.
    inicio_rig = 700_000
    for row, (name, role, label, desplazamiento) in enumerate(ROL_ARCHIVOS):
        dx, dy, rot = desplazamiento
        controles = conf['piezas'][name]
        base_params = {'escala': args.escala}
        base_params.update(controles.get('encuadre', {}))
        if 'rotacion' in base_params:
            base_params['giro'] = base_params.pop('rotacion')
        base = enc(**base_params)
        separado = enc(base['escala']*.91, base['x']+dx, base['y']+dy, base['rotacion']+rot)
        enfatizado = dict(base, opacidad=1. if role == 'CABEZA' else 0.)
        # Cada pieza vuelve en un momento distinto: cabeza→cabello→manos/cuerpo.
        restaurar = 5_250_000 if role == 'CABEZA' else 5_700_000 if role == 'CABELLO' else 6_400_000
        poses = [(700000, base), (1300000, base), (2150000, separado),
                 (3000000, separado), (3800000, base), (4500000, enfatizado),
                 (5000000, enfatizado), (restaurar, base)]
        # Zoom de montaje común: igual transformación para todas las piezas.
        # Mantiene la intro original y sólo acerca la composición antes del baile.
        inicio_zoom = min(6_400_000, inicio_final-1)
        if poses[-1][0] < inicio_zoom:
            poses.append((inicio_zoom, base))
        final_pose = dict(base, escala=base['escala']*zoom_final,
                          x=base['x']*zoom_final+delta_x,
                          y=base['y']*zoom_final+delta_y)
        poses.append((inicio_final, final_pose))
        if fundido_final:
            poses.append((fin-fundido_final, final_pose))
            poses.append((fin-1, dict(final_pose, opacidad=0.)))
        claves = [clave(t-inicio_rig, e) for t, e in poses]
        controles = conf['piezas'][name]
        hb = {'rol': role, 'inicio': inicio_final-inicio_rig, 'fin': fin-inicio_rig,
              'bpm': args.bpm, 'intensidad': 1., 'desfase': 0, 'rapidez': 1.,
              'retardo': 80000, 'cuelloX': conf['cuelloX'], 'cuelloY': conf['cuelloY'],
              'pivoteX': conf['cuelloX'], 'pivoteY': conf['cuelloY'],
              'fundido': 120000, 'giro': conf['giro'], 'rebote': conf['rebote'],
              'activo': True, 'recorte': 0, 'duracionBase': fin-inicio_final}
        hb.update({k: v for k, v in controles.items() if k not in
                   ('encuadre', 'geometriaAplicada', 'marcoReferenciaUV', 'tamanoReferencia')})
        c = clip(fin-inicio_rig, source=origen(args.assets/name, nombre=label+'.png'),
                 pose=base, claves=claves, head_bop=hb)
        layer(c, inicio_rig, row, label)

    def texto(contenido, inicio, hasta, y, tamano=.025, x=0., color=-13750738,
              fondo_color=0, negrita=True, fila=12):
        data = {'contenido': contenido, 'tamano': tamano, 'color': color,
                'fuente': 'SANS', 'negrita': negrita, 'contorno': 0.,
                'colorContorno': -16777216, 'sombra': False, 'fondo': fondo_color,
                'alineacion': 'CENTRO', 'espaciado': .02}
        c = clip(hasta-inicio, texto=data, pose=enc(1., x=x, y=y))
        movimiento = {'tipo': 'APARECER', 'intensidad': 1., 'curva': 'SUAVE', 'desde': 0., 'hasta': 1.}
        c['entrada'] = [copy.deepcopy(movimiento)]
        c['salida'] = [copy.deepcopy(movimiento)]
        layer(c, inicio, fila, contenido)

    if not args.sin_textos:
        texto('DE UNA FOTO A UN RIG', 150000, 1400000, -.445, .026)
        texto('6 PIEZAS · 1 RITMO', 1600000, 3250000, -.445, .026)
        texto('PIVOTE EN EL CUELLO', 3850000, 4950000, .445, .023)
        texto('+', 3950000, 4900000,
              conf.get('cuelloEscenaY', .5+args.escala*(conf['cuelloY']-.5))-.5, .040,
              x=conf.get('cuelloEscenaX', .5+args.escala*(conf['cuelloX']-.5))-.5, color=-10642989)
        texto('CABEZA → CABELLO → MANOS', 5100000, 6600000, .445, .022)
    if not args.sin_credito:
        texto('RAGI', 1100000, fin, .475, .020, color=-9342607, negrita=True, fila=13)
    if fundido_final and not args.sin_credito:
        credito = project['capas'][-1]['clip']
        credito['claves'] = [clave(fin-fundido_final-1100000, credito['encuadre']),
                             clave(fin-1100000-1, dict(credito['encuadre'], opacidad=0.))]

    contador += 1
    project['audios'].append({'id': contador,
        'origen': origen(args.musica, 'AUDIO', 'Música original · sin outro.m4a', 19_899_501),
        'inicio': 0, 'desde': 0, 'hasta': fin, 'volumen': 1.,
        'fundidoEntrada': 15000, 'fundidoSalida': 180000})
    project['siguienteId'] = contador+1
    nombre_json = 'headbop-provisional.json' if args.draft else 'headbop-proyecto.json'
    manifiesto = args.out/nombre_json
    contenido = json.dumps(project, ensure_ascii=False, indent=2)
    manifiesto.write_text(contenido, encoding='utf-8')
    manifest_check(project, medios, strict=not args.draft)
    paquete = None
    if not missing:
        paquete = args.out/'headbop-test2xd.royer'
        # Escritura transaccional: nunca deja un .royer truncado con nombre final.
        temporal = paquete.with_suffix('.royer.tmp')
        with zipfile.ZipFile(temporal, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
            z.writestr('proyecto.json', contenido)
            for ruta, path in medios:
                z.write(path, ruta)
        temporal.replace(paquete)
        comprobar_paquete(paquete, project, medios, args.sin_textos, args.sin_credito)
    informe = {'provisional': bool(missing), 'faltantes': missing,
        'registro_assets': str(args.registro) if args.registro else None,
        'version_entrega': 4, 'sin_textos_explicativos': args.sin_textos,
        'credito_RAGI': not args.sin_credito,
        'geometria_png': geometria,
        'revision_render_nativo': 'pendiente',
        'formato': 'CUADRADO', 'salida': [1080,1080], 'fps_motor': 30,
        'duracion_us': fin, 'inicio_final_us': inicio_final, 'bpm': args.bpm,
        'zoomFinal': zoom_final, 'desplazamientoFinal': [delta_x, delta_y],
        'rebote_comun': conf['rebote'], 'fundidoFinalUs': fundido_final,
        'claves_total': sum(len(c['claves']) for c in project['principal'])+
                       sum(len(k['clip']['claves']) for k in project['capas']),
        'claves_intro': sum(1 for k in project['capas'] for q in k['clip']['claves']
                            if q['t']+k['inicio'] <= inicio_final),
        'claves_fundido_final': sum(1 for k in project['capas'] for q in k['clip']['claves']
                                   if q['t']+k['inicio'] >= fin-fundido_final),
        'claves_del_baile': 0, 'herramientas_head_bop': 6,
        'instancias_efectos': 0, 'capas': len(project['capas']),
        'medios': len(medios), 'piezas_validadas': stats, 'capas_detalle': registro_capas,
        'paquete': str(paquete) if paquete else None,
        'sha256': hashlib.sha256(paquete.read_bytes()).hexdigest() if paquete else None,
        'nota': 'No es una réplica medida del tutorial. Intro propia y headbop propio basados en técnicas de despiece y follow-through. Pendiente inspección del render nativo.'}
    (args.out/'informe-headbop.json').write_text(json.dumps(informe, ensure_ascii=False, indent=2), encoding='utf-8')
    (args.out/'controles-headbop.json').write_text(json.dumps(conf, ensure_ascii=False, indent=2), encoding='utf-8')
    escribir_readme(args, informe)
    print(json.dumps({k: informe[k] for k in ('provisional','faltantes','duracion_us','claves_intro','claves_total','claves_fundido_final','claves_del_baile','herramientas_head_bop','capas','paquete')}, ensure_ascii=False, indent=2))
    return informe


def manifest_check(project, medios, strict):
    ids = [c['id'] for c in project['principal']]+[k['clip']['id'] for k in project['capas']]+[a['id'] for a in project['audios']]
    assert len(ids) == len(set(ids)), 'IDs duplicados'
    assert project['siguienteId'] > max(ids)
    for c in project['principal']+[k['clip'] for k in project['capas']]:
        assert c['hasta'] > c['desde']
        assert sum(k in c for k in ('origen','texto')) == 1
        tiempos = [k['t'] for k in c['claves']]
        assert tiempos == sorted(set(tiempos)), 'Claves fuera de orden o duplicadas'
        assert not tiempos or (tiempos[0] >= 0 and tiempos[-1] <= c['hasta'])
        if c.get('headBop'):
            h = c['headBop']
            assert 0 <= h['inicio'] < h['fin'] <= c['hasta']
    rutas = {r for r, _ in medios}
    assert len(rutas) == len(medios)
    for ruta, path in medios:
        if strict:
            assert path.is_file() and path.stat().st_size > 0
        assert ruta.startswith('medios/') and '/' not in ruta[7:]


def comprobar_paquete(path, project, medios, sin_textos=True, sin_credito=False):
    """Comprobación mínima de entrega; no renderiza ni evalúa rendimiento."""
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None, 'ZIP con CRC inválido'
        loaded = json.loads(z.read('proyecto.json'))
        assert loaded == project, 'El manifiesto empaquetado no corresponde al generado'
        assert set(z.namelist()) == {'proyecto.json'} | {r for r, _ in medios}
        assert all(z.getinfo(r).file_size > 0 for r, _ in medios), 'Medio vacío'
        textos = [c['clip']['texto']['contenido'] for c in loaded['capas'] if 'texto' in c['clip']]
        if sin_textos:
            assert textos == ([] if sin_credito else ['RAGI']), 'Persisten rótulos explicativos'
        assert len([c for c in loaded['capas'] if 'headBop' in c['clip']]) == 6


def escribir_readme(args, info):
    texto = f'''# Head bop V4 editable en Royer Method

Este paquete contiene capas PNG transparentes, claves limitadas a la intro y seis instancias de la herramienta procedural Head bop. V4 omite por defecto todas las capas de texto añadidas, incluido el signo + y el crédito nativo. La fotografía original se conserva intacta y su autoría corresponde a **RAGI**. No contiene una animación renderizada ni un video externo ocultando el resultado.

## Abrir y ajustar

1. Abrir `headbop-test2xd.royer` en la variante de prueba con soporte Head bop.
2. Seleccionar una capa y entrar en **Animar → Head bop**.
3. Ajustar rol, BPM, intensidad, giro, rebote, inicio/final y fundido. Para cabeza, fijar el pivote/cuello. Para cabello, ajustar pivote, retardo y arrastre. Las manos conservan pivotes independientes.
4. Mantener BPM y fase compatibles entre las capas. La música y el inicio final global están en {args.inicio_final:.3f}s.
5. Para cambiar el despiece de presentación, editar las pocas claves de encuadre anteriores al baile. No hay claves por cada golpe musical.
6. La fuente original de **RAGI** se conserva intacta, incluido su crédito en la fotografía. Los PNG reconstruidos son piezas derivadas para animación; se debe comprobar su encaje visual en el render nativo.

## Estructura

- Intro: original → despiece → cabeza → cabello/manos → reunión. Se conserva su dirección y música, sin rótulos superpuestos.
- Final: {args.inicio_final:.3f}–{args.duracion:.3f}s, aproximadamente {args.bpm:.3f} BPM.
- {info['claves_intro']} claves de intro, **0 claves del baile**, {info['claves_fundido_final']} claves sólo de fundido final: {info['claves_total']} claves totales. Seis herramientas Head bop, cero efectos de shake/desenfoque añadidos.
- Zoom final común{info['zoomFinal']:.3f} y desplazamiento{info['desplazamientoFinal']}; rebote común{info['rebote_comun']:.3f}; fundido final{info['fundidoFinalUs']/1e6:.3f}s. Todos modificables en controles-headbop.json: zoomFinal, desplazamientoFinalX/Y, rebote y fundidoFinalUs.
- Fondo y proyecto cuadrados; exportación1080×1080,30fps,muestras4. La app fija los fps del motor, no el manifiesto.
- La música original se recorta antes del outro TikTok. No se incluye el video tutorial como imagen ni su marca visual.

## Regenerar

`python crear_headbop-v4.py --assets assets-v4 --config proyecto-headbop-v4/ajustes-headbop-v4.json --out proyecto-headbop-v4`

Para revisar el proyecto sin tener aún todas las piezas:

`python crear_headbop-v4.py --draft`

El punto de partida es `proyecto-headbop-v3/controles-headbop.json`; `--config` aplica ajustes parciales encima. Cada pieza admite un objeto `encuadre` con `escala`, `x`, `y` y `rotacion`. También admite `--escala`, `--bpm`, `--inicio-final`, `--duracion`, `--original` y `--musica`. Para reutilizar el archivo completo de controles de esta V4, pasar `--base-config proyecto-headbop-v4/controles-headbop.json` sin volver a aplicar los ajustes parciales.

Los PNG pueden tener tamaños diferentes. Si conservan la relación de aspecto y framing, no necesitan cambios. Si se prolonga el lienzo, indicar `marcoReferenciaUV: [izquierda, arriba, derecha, abajo]`, el rectángulo normalizado que contiene el lienzo anterior en el PNG nuevo. Ejemplo: al extender un cuadrado hacia abajo a 1024×1536, usar `[0, 0, 1, 0.6666666667]`. El generador recalcula escala/centro, pivotes y cuello con ENCAJAR; rechaza transformaciones que deformen la imagen. El informe registra la geometría aplicada. Los PNG originales y V1/V2/V3 permanecen intactos.

El modo estricto rechaza PNG sin alpha real, piezas vacías y medios faltantes. La comprobación mínima revisa IDs, orden de claves, intervalos, rutas, CRC del ZIP, medios no vacíos y ausencia de los rótulos retirados. **Esto no sustituye a inspeccionar la vista previa y la exportación de la app.** La exportación final debe realizarla Royer Method.

No está confirmada coincidencia visual con la referencia. El BPM es una estimación por señal: subdivisión medida0,278643s y candidato107,665BPM, sin escucha semántica del audio.
'''
    (args.out/'README.md').write_text(texto, encoding='utf-8')


if __name__ == '__main__':
    build(argparser().parse_args())



