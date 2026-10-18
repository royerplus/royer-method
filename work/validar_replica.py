from pathlib import Path
import csv, hashlib, json, re, zipfile
base=Path(r'C:\Users\PC\Downloads\royer-method-work')
out=base/'resultados'
p=json.loads((out/'replica-testprueba1-proyecto.json').read_text(encoding='utf8'))
repo=Path(r'C:\Users\PC\Downloads\royer-method')
enums=(repo/'app/src/main/java/com/royermethod/app/editor/modelo/EfectoVideo.kt').read_text(encoding='utf8')
known=set(re.findall(r'^\s+([A-Z_]+)\(',enums,re.M))
ids=[]
for c in p['principal']+[k['clip'] for k in p['capas']]:
 ids.append(c['id'])
 assert c['hasta']>c['desde']
 times=[k['t'] for k in c['claves']]
 assert times==sorted(set(times))
 assert all(0<=t<=c['hasta']-c['desde'] for t in times)
 for e in c['efectos']:
  assert e['tipo'] in known,e['tipo']
  assert 0<=e['inicio']<e['fin']<=c['hasta']-c['desde']
ids += [a['id'] for a in p['audios']]
assert len(ids)==len(set(ids))
assert sorted(set(k['fila'] for k in p['capas']))==[1,2,3]
rows=list(csv.DictReader((out/'replica-testprueba1-parametros.csv').open(encoding='utf-8-sig')))
lines=['# Réplica editable testprueba1','',
'Proyecto de13.700 segundos en formato3:4 (576×768,30fps). Contiene las12 fotos aportadas, el comienzo original de la referencia, un fotograma del intro a4.933s y una única pista de audio extraída hasta13.700s. El outro TikTok de13.700–17.700s queda fuera del montaje.','',
'El archivo `replica-testprueba1.royer` es un ZIP portátil con `proyecto.json` y17 medios. Todos los movimientos, máscaras, tiempos, recortes, intensidades y encuadres se conservan como datos editables; el montaje no se ha renderizado fuera del editor.','',
'Los recursos originales están completos para poder reajustar recortes. Las primeras cinco fotos incluyen recortes no destructivos de las barras del teléfono. El intro queda silenciado para que su sonido no se duplique con la pista de audio.','',
'## Controles usados','',
'-71 tramos de efectos: bordes en espejo, desenfoques direccionales, sacudidas pequeñas, dos iris de cierre, dos aperturas de círculo negro y dos cierres verticales.','-Fotogramas clave de zoom, rebote, desplazamiento, rotación y opacidad; intensidad y tiempos ajustables desde el editor.','-Dos entradas REVELAR lineales sobre otra foto: revelado de izquierda a derecha durante el solape.','-Tres filas de capas:1 para fotos base;2 para iris/superposiciones/wipes;3 para intro y blanco final. La pista principal es negra.','-C aparece detrás de B desde6.033s y9.700s; permanece visible durante el cierre del círculo.','-El blanco final se desvanece sobre negro durante0.900s.','',
'## Tabla del montaje','',
'| Bloque | Foto | Inicio s | Fin s | Fila | Efectos |','|---|---|---:|---:|---:|---|']
for r in rows:
 effects=', '.join(e['tipo'] for e in json.loads(r['efectos'])) or '—'
 photo=r['foto'].split(':')[0] if r['foto'] else '—'
 lines.append(f"| {r['plano']} | {photo} | {int(r['inicio_us'])/1e6:.6f} | {int(r['fin_us'])/1e6:.6f} | {r['fila']} | {effects} |")
lines += ['', '## Archivos y validación','',
'-`replica-testprueba1.royer`: importar como proyecto con la nueva versión del editor.','-`replica-testprueba1-proyecto.json`: manifiesto legible, mismo contenido que el paquete.','-`replica-testprueba1-parametros.csv`: tabla completa con recortes y cada parámetro de efectos/animaciones.','-`replica-testprueba1-validacion.json`: tamaño y huella SHA-256 del paquete.','-`../crear_replica.py`: generador reproducible; necesita Python con Pillow y FFmpeg.','',
'Comprobado: ZIP íntegro, todas las URI locales presentes,12 fotos distintas utilizadas, identificadores únicos, claves ordenadas,71 efectos reconocidos por los enums Kotlin actuales y todos sus intervalos dentro de sus clips. Estos controles estructurales no sustituyen la comprobación visual de la exportación en la app.','',
'API de persistencia usada: `version=1`; `formato=RETRATO_3_4`; `principal[]`, `capas[{inicio,fila,mezcla,clip}]`, `audios[]`, `siguienteId`; clips con `origen`, `encuadre`, `claves[{t,encuadre,curva}]`, `entrada/salida` y `efectos[{tipo,inicio,fin,intensidad,rapidez,desfase,fundidoEntrada,fundidoSalida,duracionBase,activo}]`. Paquete: `proyecto.json` y nombres estrictos `medios/00000.ext`.','',
'La réplica adapta el estilo y el ritmo a fotografías diferentes. Los nombres de efectos son descripciones de lo observado; no identifican con certeza los presets de la aplicación original.']
(out/'replica-testprueba1-LEEME.md').write_text('\n'.join(lines)+'\n',encoding='utf8')
print(json.dumps({'validacion':'OK','ids':len(ids),'filas':[1,2,3],'tipos_efectos':sorted({e['tipo'] for k in p['capas'] for e in k['clip']['efectos']})},ensure_ascii=False))