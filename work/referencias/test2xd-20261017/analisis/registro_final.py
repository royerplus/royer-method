"""Produce medidas y encuadres; no modifica ni genera imágenes."""
from pathlib import Path
import sys,json,math,hashlib
sys.path.insert(0,'C:/Users/PC/Downloads/royer-method-work/python')
import cv2,numpy as np
P=Path(__file__).resolve().parents[1]
raw=json.loads((P/'analisis/registro-sift.json').read_text('utf8'))
w,h=raw['original_size']; global_scale=.84
neck=np.array([.48*w,.61*h,1.])
out={'version':1,'original':{'file':'entrada/Screenshot_20260623-190945.jpg','width':w,'height':h,'encuadre':{'escala':global_scale,'x':0.,'y':0.,'rotacion':0.},'neck_uv':[.48,.61]},'canvas_output':[1080,1080],'neck_scene_uv':[.5+global_scale*(neck[0]-w/2)/w,.5+global_scale*(neck[1]-h/2)/w],'status':'PROVISIONAL: requiere QA compuesta nativa antes de entrega','assets':{},'notes':['PNG generados redibujan/recentran/escalan piezas: la similitud no garantiza coincidencia exacta de toda silueta.','Sólo cabeza/flequillo conservan suficientes correspondencias distribuidas para registro automático útil.','Cuerpo y manos se anclaron manualmente por uniones anatómicas; cabello trasero por corona/masa global.','CuelloUV fuera de0..1 es válido para un recorte cuyo cuello queda fuera del lienzo PNG. No clamp.','Todos los cuellos proyectan matemáticamente al mismo punto de escena, también con escalas/centros diferentes.','Rotación positiva horaria conYhaciaabajo, igual que Encuadre de Royer Method.','No se ha modificado ningún PNG en este análisis.']}

def manual(s,deg,src,dst):
    a=math.radians(deg); R=np.array([[math.cos(a),-math.sin(a)],[math.sin(a),math.cos(a)]])*s
    return np.column_stack([R,np.array(dst)-R@np.array(src)])

for name,info in raw['assets'].items():
    ww,hh=info['asset_size']
    automatic=name in ('cabeza.png','flequillo.png')
    if automatic:
        M=np.array(info['matrix_asset_pixel_to_original_pixel']);method='SIFT/RANSAC';confidence='MEDIA-ALTA' if name=='cabeza.png' else 'MEDIA';reason='Ajuste local con inliers, sujeto a redibujo no uniforme.'
    elif name=='cuerpo.png':
        # SIFT sólo dio collar, no vestido; escala/orientación de partida y ancla manual.
        M=manual(.6257807518691929,-7.681670608477889,[625,440],neck[:2]);method='ANCLA MANUAL CUELLO, escala orientativa';confidence='BAJA';reason='SIFT sólo cubría129x269px en collar; se descarta como prueba de registro de todo el cuerpo. Ancla manual evita cuello separado del mentón.'
    elif name=='cabello-trasero.png':
        M=manual(.90,0.,[500,20],[440,8]);method='CORONA Y MASA MANUAL';confidence='BAJA';reason='SIFT degenerado rechazado. Masa posterior por detrás de cabeza/cuerpo, puntas largas pueden exceder cuadro como en la fuente.'
    elif name=='brazo-elevado.png':
        M=manual(.6457335270915021,-7.850788124764435,[750,1060],[780,580]);method='ANCLA MANUAL UNIÓN HOMBRO';confidence='BAJA';reason='Sólo5inliers SIFT, insuficientes. Se fija unión de ropa/hombro y se conserva escala/orientación como aproximación visual.'
    elif name=='brazo-frontal.png':
        M=manual(.38,0.,[950,240],[687,812]);method='ANCLA MANUAL MANO Y MANGA';confidence='BAJA';reason='SIFT produjo88grados falsos, rechazado. Fist coincide aproximadamente; extremo de manga se prolonga fuera del cuadro inferior.'
    else: continue
    T=np.vstack([M,[0,0,1.]])
    center=M@np.array([ww/2,hh/2,1.]);scale=float(np.linalg.norm(M[:,0])); angle=math.degrees(math.atan2(M[1,0],M[0,0]))
    e={'escala':float(global_scale*scale*ww/w),'x':float(global_scale*(center[0]-w/2)/w),'y':float(global_scale*(center[1]-h/2)/w),'rotacion':float(angle)}
    uv=(np.linalg.inv(T)@neck)[:2]/np.array([ww,hh])
    alpha=cv2.imread(str(P/'assets'/name),cv2.IMREAD_UNCHANGED)[:,:,3]
    yy,xx=np.where(alpha>8)
    item={'file':str(P/'assets'/name),'sha256':hashlib.sha256((P/'assets'/name).read_bytes()).hexdigest(),'size':[ww,hh],'alpha_bbox_xyxy':[int(xx.min()),int(yy.min()),int(xx.max()+1),int(yy.max()+1)],'encuadre':e,'cuelloUV':{'pivoteX':float(uv[0]),'pivoteY':float(uv[1])},'matrix_asset_px_to_original_px':M.tolist(),'confidence':confidence,'method':method,'reason':reason,'sift_diagnostic':{k:info[k] for k in ['matches_ratio_076','inliers','inlier_fraction','residual_median_px','asset_inlier_bbox_xywh']},'native_composite_verified':False}
    if name in ('cabello-trasero.png','flequillo.png'):
        root_px=[500,50] if name=='cabello-trasero.png' else [540,145]
        item['raizUV']={'pivoteX':root_px[0]/ww,'pivoteY':root_px[1]/hh}
    elif name.startswith('brazo'):
        join_px=[750,1060] if name=='brazo-elevado.png' else [490,1160]
        item['manoUV']={'pivoteX':join_px[0]/ww,'pivoteY':join_px[1]/hh}
        q=M@np.array([*join_px,1.]);item['union_original_uv']=[float(q[0]/w),float(q[1]/h)]
    elif name=='cuerpo.png':
        item['raizUV']={'pivoteX':.52,'pivoteY':.88}
    out['assets'][name]=item
(P/'registro-assets.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'neck_scene_uv':out['neck_scene_uv'],'assets':{k:{q:v[q] for q in ['encuadre','cuelloUV','confidence']} for k,v in out['assets'].items()}},ensure_ascii=False,indent=2))
