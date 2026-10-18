"""Proyecto nativo con trayectorias medidas; el montaje lo renderiza Royer.

--diagnostico usa fotogramas de registro de la referencia para comprobar geometría.
La entrega normal usa las doce fotos del usuario, conservando los originales.
"""
from pathlib import Path
import argparse,csv,hashlib,json,math,zipfile
from PIL import Image
from crear_replica import enc,effect,frame,END,SOURCE,OUT,ASSETS,REF_NAME

BASE=Path(__file__).parent
FORENSE=BASE/'analisis/forense'

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--diagnostico',action='store_true');args=ap.parse_args()
    measured=json.loads((FORENSE/'ajustes-medidos.json').read_text())
    gh=json.loads((FORENSE/'gh-final-mediciones.json').read_text())
    shots={**measured['shots'],**gh['shots']}
    name='diagnostico-medido' if args.diagnostico else 'replica-medida-testprueba1'
    media=[];registered={};next_id=1;parameters=[];uncertain=[]
    photos=sorted(SOURCE.glob('*.jpg'));assert len(photos)==12
    def origin(path,kind='IMAGEN',duration=0,fps=0.,audio=False,display=None):
        path=path.resolve()
        if path in registered:return dict(registered[path])
        route=f'medios/{len(media):05d}{path.suffix.lower()}'
        w,h=Image.open(path).size if kind=='IMAGEN' else ((576,768) if kind=='VIDEO' else (0,0))
        o=dict(uri=route,nombre=display or path.name,tipo=kind,ancho=w,alto=h,duracion=duration,fps=fps,conAudio=audio,giro=0,bytes=path.stat().st_size)
        media.append((route,path));registered[path]=o;return dict(o)
    def clip(o,duration,framing=None,keys=None,fx=None,pieces=None):
        nonlocal next_id
        c=dict(id=next_id,origen=o,desde=0,hasta=duration,velocidad=1.,volumen=0.,mantenerTono=True,fundidoEntrada=0,fundidoSalida=0,
               color=[0.]*10,filtro='NINGUNO',intensidadFiltro=1.,encuadre=framing or enc(1.),claves=keys or [],piezas=pieces or [],entrada=[],salida=[],duracionEntrada=100000,duracionSalida=100000,continua=[],efectos=fx or [],transicion='NINGUNA',duracionTransicion=0,ajusteTransicion=dict(curva='LINEAL',angulo=180.,color=-1,intensidad=1.),fluido=False,calidad=1.)
        next_id+=1;return c
    black=origin(ASSETS/'negro.png');white=origin(ASSETS/'blanco.png')
    project=dict(version=1,formato='RETRATO_3_4',fondo='NEGRO',salida=dict(ladoCorto=576,calidad='ALTA',hevc=False,desenfoque=True,muestras=8),principal=[clip(black,END)],capas=[],audios=[])
    def layer(c,start,row,label,notes=''):
        project['capas'].append(dict(inicio=frame(start),fila=row,mezcla='NORMAL',clip=c))
        parameters.append(dict(plano=label,inicioFrame=start,finFrame=round((frame(start)+c['hasta'])*30/1e6),claves=len(c['claves']),partes=len(c['piezas']),efectos=','.join(x['tipo'] for x in c['efectos']),notas=notes))
    intro=origin(SOURCE/REF_NAME,'VIDEO',17700000,30.,True,'Intro original.mp4')
    layer(clip(intro,frame(149)),0,0,'Intro original')
    freeze=origin(ASSETS/'intro-congelado-4.933333.jpg')
    layer(clip(freeze,frame(159)-frame(149)),149,0,'Intro fijo bajo las mitades','Sin aplastamiento')
    crops=[[0.,184/2436,0.,318/2436],[0.,109/2436,0.,120/2436],[0.,109/2436,0.,120/2436],[0.,109/2436,0.,258/2436],[0.,109/2436,0.,120/2436]]+[[0.,0.,0.,0.] for _ in range(7)]
    y_bias=[.10,.10,.08,0.,.025,0.,.10,.025,0.,.075,.06,.02]
    definitions=[('A1',0,149,172,1),('B1',1,172,187,1),('C1',2,187,200,1),('D1',3,200,214,1),('E1',4,214,228,1),('F1',5,228,241,1),('G1',6,232,255,2),('H1',7,255,269,1),('A2',8,259,282,2),('B2',9,282,297,1),('C2',10,297,310,1),('D2',11,310,324,1),('E2',0,324,338,1),('F2',2,338,352,1),('G2',4,342,365,2),('H2',6,365,379,1)]
    def pose_at(poses,n):
        values=sorted([p for p in poses if p.get('escala') is not None],key=lambda p:p['frame'])
        if n<values[0]['frame']:a,b=values[:2]
        elif n>values[-1]['frame']:a,b=values[-2:]
        else:a=max((p for p in values if p['frame']<=n),key=lambda p:p['frame']);b=min((p for p in values if p['frame']>=n),key=lambda p:p['frame'])
        if a['frame']==b['frame']:return a
        f=(n-a['frame'])/(b['frame']-a['frame'])
        p={k:a[k]+(b[k]-a[k])*f for k in ['escala','rotacion','x','y']}
        p['escala']=max(.25,p['escala']);return p
    def framing(p,base):
        s=p['escala'];r=math.radians(p['rotacion']);x0=base['x'];y0=base['y']
        return enc(base['escala']*s,p['x']+s*(math.cos(r)*x0-math.sin(r)*y0*768/576),p['y']+s*(math.sin(r)*x0*576/768+math.cos(r)*y0),p['rotacion'],crop=base['recorte'])
    entry1=json.loads((FORENSE/'entrada-mitad-mediciones.json').read_text())
    # La segunda entrada se mide separada: no se le impone la duración primera.
    entry2file=FORENSE/'entrada-mitad-segunda.json'
    entry2=json.loads(entry2file.read_text()) if entry2file.exists() else []
    radiiB=[386,349,310,268,222,174];radiiC=[138,117,97,77,60,44,30,18,9,0]
    # Cuadros de cambio de plano con rastro de movimiento (latigazo). Medidos en
    # latigazo.py/diagonal.py: el cuadro es la media del siguiente desplazado de
    # 'desde' a 'hasta' px con bordes en espejo. Primer ciclo; el segundo repite
    # el mismo edit (+110). B282 dio un ajuste flojo y se usa el de B172.
    FR=frame(1)
    whips={}
    for r in json.loads((FORENSE/'latigazos-medidos.json').read_text()):
        if r['frame']>260:continue
        size=576 if r['eje']=='x' else 768
        c=(r['centro']+size)%(2*size)-size
        whips[r['frame']]=dict(eje=r['eje'],desp=c,largo=r['largo'],ref=r['frame']+1)
    for r in json.loads((FORENSE/'latigazos-diagonales.json').read_text()):
        if r['frame']>260:continue
        # 100-105 grados: casi vertical; se usa solo la componente vertical
        whips[r['frame']]=dict(eje='y',desp=(r['desde']+r['hasta'])/2,largo=r['largo'],ref=r['referencia'])
    for n in list(whips):whips[n+110]={**whips[n],'ref':whips[n]['ref']+110}
    # Rastro que queda en el cuadro siguiente al latigazo (la pose medida no
    # cambia, solo el desenfoque). latigazos-residuales.json, mismo modelo con
    # angulo libre: ambos ciclos dan lo mismo salvo G (351 no ajusta; se usa 352).
    trails={}
    for r in json.loads((FORENSE/'latigazos-residuales.json').read_text()):
        if r['frame'] in (351,):continue
        a=math.radians(r['angulo']);L=r['largo']
        # Componentes por eje. El motor solo traza la diagonal que baja a la
        # derecha (X e Y a la vez); la otra (90-180 grados) o casi un eje
        # (menos de 21 grados) se queda en su eje principal.
        lx,ly=abs(math.cos(a))*L,abs(math.sin(a))*L
        if r['angulo']%180>90 or ly<L*math.sin(math.radians(21)):ly=0 if lx>=ly else ly
        if r['angulo']%180>90 or lx<L*math.sin(math.radians(21)):lx=0 if ly>lx else lx
        trails[r['frame']]=dict(x=lx,y=ly,angulo=r['angulo'],largo=L)
    def hold(points,lead=0):
        """Progreso por cuadro, quieto mientras el obturador esta abierto (+-0.25)."""
        out=[]
        for q in points:
            for dt in (-0.4*FR,0.4*FR):
                t=q['t']+lead+dt
                if t>=0:out.append(dict(t=round(t),valor=q['valor']))
        return out
    for label,photo_i,start,end,row in definitions:
        shot=label[0];repeat=label.endswith('2');offset=110 if repeat else 0
        data=shots[shot];dur=frame(end)-frame(start)
        if args.diagnostico:
            path=FORENSE/f"f-{data['stableFrame']-140:04d}.png";base=enc(1.)
        else:path=photos[photo_i];base=enc(1.,y=y_bias[photo_i],crop=crops[photo_i])
        o=origin(path,display=f'{label} · '+path.name)
        available=data['poses']
        # G y H se registraron también en el segundo ciclo. El resto comparte
        # trayectorias del mismo edit, desplazadas exactamente110fotogramas.
        actual=[p for p in available if start<=p['frame']<end] if shot in ('G','H') else [{**p,'frame':p['frame']+offset} for p in available]
        minimum=start
        if shot=='A':minimum=269 if repeat else 159
        keys=[]
        for n in range(start,end+1):
            qn=max(n,minimum)
            p=pose_at(actual,qn)
            keys.append(dict(t=frame(n)-frame(start),encuadre=framing(p,base),curva='LINEAL'))
            exact=next((p for p in actual if p['frame']==qn),None)
            if (exact is None or exact.get('escala') is None) and n>=minimum:uncertain.append(dict(plano=label,frame=n,tipo='interpolacion' if min(p['frame'] for p in actual)<qn<max(p['frame'] for p in actual) else 'borde_sin_registro'))
        fx=[effect('BORDES_ESPEJO',0,dur)]
        for n in sorted(t for t in trails if start<=t<end):
            a=max(0,round(frame(n)-frame(start)-0.4*FR));b=round(frame(n)-frame(start)+0.4*FR)
            for kind,length in (('DESENFOQUE_HORIZONTAL',trails[n]['x']),('DESENFOQUE_VERTICAL',trails[n]['y'])):
                strength=length/2/(0.04*768)
                while strength>1e-3:fx.append(effect(kind,a,b,min(3.,strength)));strength-=3.
        mine=sorted(n for n in whips if start<=n<end)
        if mine:
            poses={n:pose_at(actual,max(n,minimum)) for n in range(start,end+1)}
            for n in mine:
                w=whips[n];q=dict(pose_at(actual,max(w['ref'],minimum)))
                q['x' if w['eje']=='x' else 'y']+=w['desp']/(576 if w['eje']=='x' else 768);poses[n]=q
                strength=w['largo']/2/(0.04*768);a=max(0,round(frame(n)-frame(start)-0.4*FR));b=round(frame(n)-frame(start)+0.4*FR)
                while strength>1e-3:
                    fx.append(effect('DESENFOQUE_HORIZONTAL' if w['eje']=='x' else 'DESENFOQUE_VERTICAL',a,b,min(3.,strength)));strength-=3.
            keys=[]
            for n in range(start,end+1):
                t=frame(n)-frame(start);ts=[t]
                # El salto ocurre con el obturador cerrado: de n-1+0.3 a n-0.3
                if n in mine or n-1 in mine:ts=[t-0.3*FR,t]
                if n in mine:ts.append(t+0.3*FR)
                for ti in ts:
                    if ti>=0 and (not keys or ti>keys[-1]['t']):keys.append(dict(t=round(ti),encuadre=framing(poses[n],base),curva='LINEAL'))
        if shot=='B':
            a=181+offset;lead=round(0.4*FR);ef=effect('CIRCULO_CERRAR',frame(a)-frame(start)-lead,dur)
            ef['progresos']=hold([dict(t=frame(a+i)-frame(a),valor=1-r/484) for i,r in enumerate(radiiB)],lead);fx.append(ef)
        if shot=='C':
            ef=effect('CIRCULO_ABRIR',0,dur)
            ef['progresos']=hold([dict(t=frame(start+i)-frame(start),valor=1-r/145.2) for i,r in enumerate(radiiC)]);fx.append(ef)
        if shot=='G':
            ef=effect('REVELAR_HORIZONTAL',0,dur)
            xs=[148,222,260,280,288,290,304,332,384,576]
            ef['progresos']=hold([dict(t=frame(start+i)-frame(start),valor=x/576) for i,x in enumerate(xs)]);fx.append(ef)
        pieces=[]
        if shot=='A':
            entry=entry2 if repeat else entry1
            if not entry:raise RuntimeError('Falta medir entrada-mitad-segunda.json')
            first=keys[0]['encuadre'];crop=base['recorte']
            fit=max(576/(o['ancho']*(1-crop[0]-crop[2])),768/(o['alto']*(1-crop[1]-crop[3])))
            rad=math.radians(first['rotacion'])
            seam=(crop[1]+1-crop[3])/2+(math.sin(rad)*first['x']*576-math.cos(rad)*first['y']*768)/(o['alto']*fit*first['escala'])
            for upper,title in [(True,'Mitad superior'),(False,'Mitad inferior')]:
                region=dict(izquierda=0.,arriba=0. if upper else seam,derecha=1.,abajo=seam if upper else 1.)
                pieces.append(dict(nombre=title,region=region,encuadre=enc(1.),claves=[dict(t=frame(p['frame'])-frame(start),encuadre=enc(1.,y=p['upperOffset'] if upper else p['lowerOffset']),curva='LINEAL') for p in entry if start<=p['frame']<=end]))
        layer(clip(o,dur,base,keys,fx,pieces),start,row,label,'Registro de geometria por fotograma; sin sacudida aleatoria')
        if shot=='B':
            # El último cuadro es un anillo, no una foto circular llena.
            # Disco negro independiente: su radio y su duración siguen editables.
            ef=effect('CIRCULO_CERRAR',0,frame(1));ef['progresos']=[dict(t=0,valor=1-163/484)]
            layer(clip(black,frame(end)-frame(end-1),fx=[ef]),end-1,2,label+' · interior negro del anillo')
    # Fade final medido en luminancia; no se supone una recta de900ms.
    final=gh['final'];start=379
    whitekeys=[dict(t=frame(p['frame'])-frame(start),encuadre=enc(1.,opacity=p['mean']/255),curva='LINEAL') for p in final if p['frame']>=start]
    layer(clip(white,END-frame(start),enc(1.),whitekeys),start,3,'Final medido')
    audio=origin(ASSETS/'audio-referencia-0-13.700.m4a','AUDIO',END,0.,True)
    project['audios']=[dict(id=next_id,origen=audio,inicio=0,desde=0,hasta=END,volumen=1.,fundidoEntrada=0,fundidoSalida=0)];project['siguienteId']=next_id+1
    text=json.dumps(project,ensure_ascii=False,indent=2);OUT.mkdir(exist_ok=True)
    (OUT/f'{name}-proyecto.json').write_text(text,encoding='utf-8')
    package=OUT/f'{name}.royer'
    with zipfile.ZipFile(package,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        z.writestr('proyecto.json',text.encode('utf-8'))
        for route,path in media:z.write(path,route)
    with (OUT/f'{name}-parametros.csv').open('w',newline='',encoding='utf-8-sig') as f:
        w=csv.DictWriter(f,fieldnames=list(parameters[0]));w.writeheader();w.writerows(parameters)
    report=dict(paquete=str(package),sha256=hashlib.sha256(package.read_bytes()).hexdigest(),medios=len(media),capas=len(project['capas']),claves=sum(len(k['clip']['claves']) for k in project['capas']),piezas=sum(len(k['clip']['piezas']) for k in project['capas']),efectos=sum(len(k['clip']['efectos']) for k in project['capas']),framesSinMedicionDirecta=uncertain,latigazos={str(n):w for n,w in sorted(whips.items())},rastros={str(n):w for n,w in sorted(trails.items())})
    (OUT/f'{name}-validacion.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report,indent=2))

if __name__=='__main__':main()
