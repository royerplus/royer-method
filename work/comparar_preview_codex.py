from pathlib import Path
import json,sys

def resumen(path):
    p=json.loads(Path(path).read_text(encoding='utf-8'))
    r={'archivo':str(path),'estado':p['estado'],'duracionRealMs':p.get('duracionRealMs')}
    for name in ['callbacks','publicacionesMotor','presentacionesTexture']:
        v=p.get(name,{})
        samples=v.get('muestras',[])
        gaps=[{'de':a['fotograma'],'a':b['fotograma'],'ms':b['ms']-a['ms']} for a,b in zip(samples,samples[1:])]
        r[name]={k:v.get(k) for k in ['indicesUnicos','fpsMedios','maxGapMs','latenciaPrimerCuadroMs','saltosDeIndice','sinMarcaAsociada'] if k in v}
        r[name]['arranque']= [g for g in gaps if g['a']<=8]
        r[name]['cuadro194']=[g for g in gaps if 192<=g['a']<=196]
        r[name]['mayoresHuecos']=sorted(gaps,key=lambda x:x['ms'],reverse=True)[:5]
        if name=='publicacionesMotor':
            r[name]['render194']=[a for a in v.get('muestrasRender',[]) if 192<=a['fotograma']<=196]
            r[name]['cpuMs']=v.get('cpuMs');r[name]['swapMs']=v.get('swapMs')
    return r

paths=sys.argv[1:]
print(json.dumps([resumen(p) for p in paths],ensure_ascii=False,indent=2))
