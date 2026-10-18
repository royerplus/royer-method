"""Genera un proyecto Royer editable. Solo extrae intro fijo y audio; no renderiza el montaje.
Ejecutar con el Python de Codex (Pillow) o cualquier Python con Pillow.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps
import argparse, csv, hashlib, json, subprocess, zipfile

BASE = Path(__file__).resolve().parent
SOURCE = BASE / 'testprueba1'
OUT = BASE / 'resultados'
ASSETS = OUT / 'medios-generados'
FFMPEG = BASE / 'python/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'
REF_NAME = '675cab0eadd1705a9794a2f651390bed.mp4'
FPS = 30
END = 13_700_000

def frame(n):
    return n * 1_000_000 // FPS

def enc(scale=1.06, x=0., y=0., angle=0., opacity=1., crop=None):
    return dict(ajuste='CUBRIR', escala=scale, x=x, y=y, rotacion=angle,
                opacidad=opacity, espejo=False, recorte=crop or [0.,0.,0.,0.])

def effect(name, start, end, strength=1., speed=1., fade_in=0, fade_out=0):
    assert 0 <= start < end, (name,start,end)
    return dict(tipo=name, inicio=start, fin=end, intensidad=strength,
                rapidez=speed, desfase=0, fundidoEntrada=fade_in,
                fundidoSalida=fade_out, duracionBase=end-start, activo=True)

def movement(name, strength=1., curve='SALIDA'):
    return dict(tipo=name, intensidad=strength, curva=curve, desde=0., hasta=1.)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--ffmpeg',type=Path,default=FFMPEG)
    args=parser.parse_args()
    OUT.mkdir(exist_ok=True)
    ASSETS.mkdir(exist_ok=True)
    ref=SOURCE/REF_NAME
    photos=sorted(SOURCE.glob('*.jpg'))
    assert len(photos)==12, f'Se esperaban12 fotos; hay{len(photos)}'

    still=ASSETS/'intro-congelado-4.933333.jpg'
    audio=ASSETS/'audio-referencia-0-13.700.m4a'
    subprocess.run([str(args.ffmpeg),'-y','-v','error','-ss','4.933333333',
                    '-i',str(ref),'-frames:v','1','-q:v','2',str(still)],check=True)
    # Recorte por tiempo a13.7s en AAC. Reencodificar elimina cualquier paquete
    # de audio que pudiera sobrepasar el fin en una copia de flujo.
    subprocess.run([str(args.ffmpeg),'-y','-v','error','-i',str(ref),'-t','13.700',
                    '-map','0:a:0','-vn','-c:a','aac','-b:a','192k',str(audio)],check=True)
    black=ASSETS/'negro.png'; white=ASSETS/'blanco.png'
    Image.new('RGB',(576,768),'black').save(black)
    Image.new('RGB',(576,768),'white').save(white)

    # Registro determinista: nombres de entrada estrictos PaqueteProyecto.MEDIO.
    media=[]; by_path={}
    def origin(path,kind='IMAGEN',duration=0,fps=0.,con_audio=False,display=None):
        path=path.resolve()
        if path in by_path:
            return dict(by_path[path])
        extension=path.suffix.lower().lstrip('.')
        route=f'medios/{len(media):05d}.{extension}'
        w,h=(Image.open(path).size if kind=='IMAGEN' else ((576,768) if kind=='VIDEO' else (0,0)))
        o=dict(uri=route,nombre=display or path.name,tipo=kind,ancho=w,alto=h,
               duracion=duration,fps=fps,conAudio=con_audio,giro=0,bytes=path.stat().st_size)
        media.append((route,path));by_path[path]=o
        return dict(o)

    black_o=origin(black,display='Fondo negro.png')
    ref_o=origin(ref,'VIDEO',17_700_000,30.,True, 'Referencia — intro original.mp4')
    still_o=origin(still,display='Intro congelado — cierre ajustable.jpg')
    photo_o=[origin(p,display=f'Foto {i+1:02d} — {p.name}') for i,p in enumerate(photos)]
    white_o=origin(white,display='Final blanco — fundido ajustable.png')
    audio_o=origin(audio,'AUDIO',END,0.,True,'Audio de referencia — hasta13.700s.m4a')
    next_id=1
    parameters=[]
    def clip(o,duration,framing=None,keys=None,effects=None,entry=None,exit=None,entry_dur=100000,exit_dur=100000, name=''):
        nonlocal next_id
        c=dict(id=next_id,origen=dict(o),desde=0,hasta=duration,velocidad=1.,volumen=0.,
               mantenerTono=True,fundidoEntrada=0,fundidoSalida=0,
               color=[0.]*10,filtro='NINGUNO',intensidadFiltro=1.,
               encuadre=framing or enc(1.),claves=keys or [],entrada=entry or [],
               salida=exit or [],duracionEntrada=entry_dur,duracionSalida=exit_dur,
               continua=[],efectos=effects or [],transicion='NINGUNA',duracionTransicion=0,
               ajusteTransicion=dict(curva='SUAVE',angulo=180.,color=-1,intensidad=1.),
               fluido=False,calidad=1.)
        next_id+=1
        return c

    project=dict(version=1,formato='RETRATO_3_4',fondo='NEGRO',
                 salida=dict(ladoCorto=576,calidad='ALTA',hevc=False,desenfoque=True,muestras=8),
                 principal=[clip(black_o,END)],capas=[],audios=[],siguienteId=0)
    def layer(c,start,row,label,photo=''):
        project['capas'].append(dict(inicio=start,fila=row,mezcla='NORMAL',clip=c))
        parameters.append(dict(id=c['id'],plano=label,foto=photo,inicio_us=start,
                               fin_us=start+c['hasta']-c['desde'],fila=row,
                               recorte=json.dumps(c['encuadre']['recorte']),
                               claves=len(c['claves']),efectos=json.dumps(c['efectos'],ensure_ascii=False),
                               entrada=json.dumps(c['entrada'],ensure_ascii=False),salida=json.dumps(c['salida'],ensure_ascii=False)))

    intro_end=frame(149) #4.966667: antes de revelar la primera foto ajena.
    photo_a_end=frame(159) #5.300000: desaparicion total del intro.
    intro=clip(ref_o,intro_end,enc(1.))
    layer(intro,0,3,'Intro original (audio silenciado)')
    freeze_dur=photo_a_end-intro_end
    freeze=clip(still_o,freeze_dur,enc(1.),effects=[effect('CERRAR_VERTICAL',0,freeze_dur)])
    layer(freeze,intro_end,3,'Intro congelado: cierre vertical al centro')

    # Recortes no destructivos: excluyen estado/navegacion del telefono y los
    # margenes blancos externos; se conservan originales completos en el paquete.
    crops=[
        [0.,184/2436,0.,(2436-2118)/2436],
        [0.,109/2436,0.,120/2436],
        [0.,109/2436,0.,120/2436],
        [0.,109/2436,0.,(2436-2178)/2436],
        [0.,109/2436,0.,120/2436],
        [0.,0.,0.,0.], [0.,0.,0.,0.], [0.,0.,0.,0.],
        [0.,0.,0.,0.], [0.,0.,0.,0.], [0.,0.,0.,0.], [0.,0.,0.,0.],
    ]
    y_bias=[.10,.10,.08,.0,.025,0.,.10,.025,0.,.075,.06,.02]
    # Entradas reales de capas. C aparece detras del iris B; G ya se revela
    # mientras F sigue presente; A2 esta detras de H antes de cerrarse la banda.
    definitions=[
        ('A1',0,149,172,1,'h'),
        ('B1',1,172,187,2,'h'),
        ('C1',2,181,200,1,'v'),
        ('D1',3,200,214,1,'v'),
        ('E1',4,214,228,1,'h'),
        ('F1',5,228,241,1,'h'),
        ('G1',6,232,255,2,'v'),
        ('H1',7,255,269,2,'v'),
        ('A2',8,261,282,1,'h'),
        ('B2',9,282,297,2,'h'),
        ('C2',10,291,310,1,'v'),
        ('D2',11,310,324,1,'v'),
        ('E2',0,324,338,1,'h'),
        ('F2',2,338,352,1,'h'),
        ('G2',4,342,365,2,'v'),
        ('H2',6,365,379,2,'v'),
    ]

    for idx,(label,photo_i,start_f,end_f,row,direction) in enumerate(definitions):
        start,end=frame(start_f),frame(end_f)
        dur=end-start
        sign=1 if idx%2==0 else -1
        crop=crops[photo_i]
        y=y_bias[photo_i]
        base=enc(1.065,y=y,crop=crop)
        # Claves de encuadre medibles/editables: impacto, sobrepaso, rebote,
        # reposo y nuevo impulso antes del corte. Sin rasterizar el resultado.
        key_values=[
            (0,enc(1.19,x=.035*sign if direction=='h' else 0.,y=y+(.06 if direction=='v' else 0.),angle=-2.2*sign,crop=crop),'SALIDA'),
            (min(90_000,dur//5),enc(1.045,x=-.012*sign,y=y,angle=.7*sign,crop=crop),'SUAVE'),
            (min(180_000,dur//2),enc(1.075,x=.006*sign,y=y,angle=-.35*sign,crop=crop),'SUAVE'),
            (max(190_000,dur-100_000),enc(1.06,x=0.,y=y,angle=0.,crop=crop),'ENTRADA'),
            (dur,enc(1.17,x=-.045*sign if direction=='h' else 0.,y=y+(-.045 if direction=='v' else 0.),angle=1.8*sign,crop=crop),'LINEAL'),
        ]
        # A permanece sereno durante revelado del intro/H; empieza su golpe
        # al quedar visible. La longitud del clip no cambia el ritmo del corte.
        if label in ('A1','A2'):
            visible=frame(159 if label=='A1' else 269)-start
            key_values=[(0,enc(1.04,y=y,crop=crop),'LINEAL'),
                        (visible,enc(1.09,y=y,angle=-.7,crop=crop),'SALIDA'),
                        (min(visible+90_000,dur-90_000),enc(1.055,y=y,angle=.5,crop=crop),'SUAVE'),
                        (max(visible+100_000,dur-100_000),enc(1.065,y=y,crop=crop),'ENTRADA'),
                        (dur,enc(1.18,x=-.055,y=y,angle=1.6,crop=crop),'LINEAL')]
        keys=[dict(t=t,encuadre=e,curva=curve) for t,e,curve in key_values]
        keys=sorted({k['t']:k for k in keys}.values(),key=lambda k:k['t'])
        blur='DESENFOQUE_HORIZONTAL' if direction=='h' else 'DESENFOQUE_VERTICAL'
        fx=[effect('BORDES_ESPEJO',0,dur),
            effect(blur,0,min(100_000,dur),.48,fade_out=min(100_000,dur)),
            effect(blur,max(0,dur-100_000),dur,.55,fade_in=min(100_000,dur)),
            effect('SACUDIDA',0,min(210_000,dur),.20,fade_out=min(210_000,dur))]
        entry=[];exit=[];entry_dur=100_000;exit_dur=100_000
        if label[0]=='B':
            close_start=frame(181 if label=='B1' else 291)-start
            fx.append(effect('CIRCULO_CERRAR',close_start,dur))
        if label[0]=='C':
            reveal_end=frame(196 if label=='C1' else 306)-start
            fx.append(effect('CIRCULO_ABRIR',0,reveal_end))
        if label[0]=='G':
            # Entrada REVELAR: G esta encima de F solo durante este wipe.
            entry=[movement('REVELAR',curve='LINEAL')]
            entry_dur=frame(241 if label=='G1' else 352)-start
        if label=='H1':
            close_start=frame(261)-start
            fx.append(effect('CERRAR_VERTICAL',close_start,dur))
        if label=='H2':
            # Latigazo final hacia blanco, luego la capa blanca realiza fade.
            fx.append(effect('DESENFOQUE_HORIZONTAL',max(0,dur-100_000),dur,.9,fade_in=100_000))
        c=clip(photo_o[photo_i],dur,base,keys,fx,entry,exit,entry_dur,exit_dur)
        if label[0]=='H':
            c['color']=[.015,.04,.05,.025,.04,0.,0.,.02,0.,0.]
        layer(c,start,row,label,f'{photo_i+1:02d}: {photos[photo_i].name}')

    final_start=frame(379) #12.633333.
    final_dur=END-final_start
    white_keys=[dict(t=0,encuadre=enc(1.,opacity=1.),curva='LINEAL'),
                dict(t=900_000,encuadre=enc(1.,opacity=0.),curva='LINEAL'),
                dict(t=final_dur,encuadre=enc(1.,opacity=0.),curva='LINEAL')]
    layer(clip(white_o,final_dur,enc(1.),white_keys),final_start,3,'Final: blanco a negro')
    project['audios']=[dict(id=next_id,origen=audio_o,inicio=0,desde=0,hasta=END,
                            volumen=1.,fundidoEntrada=0,fundidoSalida=0)]
    project['siguienteId']=next_id+1
    text=json.dumps(project,ensure_ascii=False,indent=2)
    manifest=OUT/'replica-testprueba1-proyecto.json'
    manifest.write_text(text,encoding='utf-8')
    package=OUT/'replica-testprueba1.royer'
    with zipfile.ZipFile(package,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        z.writestr('proyecto.json',text.encode('utf-8'))
        for route,path in media:z.write(path,route)
    with (OUT/'replica-testprueba1-parametros.csv').open('w',newline='',encoding='utf-8-sig') as f:
        writer=csv.DictWriter(f,fieldnames=list(parameters[0]));writer.writeheader();writer.writerows(parameters)
    # Validacion estructural propia; el lector Kotlin valida ademas compatibilidad.
    used={c['origen']['uri'] for c in project['principal']}
    used|={k['clip']['origen']['uri'] for k in project['capas']}
    used|={a['origen']['uri'] for a in project['audios']}
    with zipfile.ZipFile(package) as z:
        assert set(z.namelist())=={'proyecto.json'}|used
        assert z.testzip() is None
    assert len({k['clip']['origen']['uri'] for k in project['capas'] if k['clip']['origen']['uri'] in {o['uri'] for o in photo_o}})==12
    assert all(c['volumen']==0 for c in project['principal']+[k['clip'] for k in project['capas']])
    assert max(k['inicio']+k['clip']['hasta'] for k in project['capas'])==END
    sha=hashlib.sha256(package.read_bytes()).hexdigest()
    data=dict(paquete=str(package),bytes=package.stat().st_size,sha256=sha,
              medios=len(media),fotos=12,capas=len(project['capas']),duracion_us=END,
              efectos=sum(len(k['clip']['efectos']) for k in project['capas']))
    (OUT/'replica-testprueba1-validacion.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(data,ensure_ascii=False,indent=2))

if __name__=='__main__':main()