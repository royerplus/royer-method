import sys,json,zipfile,hashlib,math
from pathlib import Path
sys.path.insert(0,r'C:/Users/PC/Downloads/royer-method-work/python')
import numpy as np,cv2
B=Path(r'C:/Users/PC/Downloads/royer-method-work/referencias/test2xd-20261017')
D=B/'analisis';D.mkdir(exist_ok=True)
o=cv2.imread(str(B/'entrada/Screenshot_20260623-190945.jpg'))
h=cv2.imread(str(B/'assets-v4/cabeza.png'),cv2.IMREAD_UNCHANGED)
sift=cv2.SIFT_create(nfeatures=5000)
mo=np.zeros(o.shape[:2],np.uint8);mo[295:630,320:700]=255
mh=np.zeros(h.shape[:2],np.uint8);mh[620:1090,510:1045]=255
ko,do=sift.detectAndCompute(cv2.cvtColor(o,cv2.COLOR_BGR2GRAY),mo)
kh,dh=sift.detectAndCompute(cv2.cvtColor(h,cv2.COLOR_BGRA2GRAY),mh)
matches=cv2.BFMatcher().knnMatch(dh,do,k=2)
good=[m for m,n in matches if m.distance<.76*n.distance]
ph=np.array([kh[m.queryIdx].pt for m in good],float)
po=np.array([ko[m.trainIdx].pt for m in good],float)
T,inl=cv2.estimateAffinePartial2D(ph,po,method=cv2.RANSAC,ransacReprojThreshold=4,maxIters=20000,confidence=.999,refineIters=50)
print('features',len(kh),len(ko),'good',len(good),'inl',int(inl.sum()),'matrix',T)
ins=inl[:,0]>0
with zipfile.ZipFile(B/'proyecto-headbop-v8/headbop-test2xd.royer') as z:j=json.loads(z.read('proyecto.json'))
foto=next(l['clip'] for l in j['capas'] if l['clip']['id']==2)
head=next(l['clip'] for l in j['capas'] if l['clip']['id']==6)
def place(e,w,h):
    s=min(1080/w,1080/h)*e['escala'];r=math.radians(e['rotacion']); c=math.cos(r);a=math.sin(r)
    R=s*np.array([[c,-a],[a,c]])
    xy=np.array([540+1080*e['x'],540+1080*e['y']])-R@np.array([w/2,h/2])
    M=np.eye(3);M[:2,:2]=R;M[:2,2]=xy;return M
H=place(head['encuadre'],1254,1254);O=place(foto['encuadre'],1079,1071)
T3=np.eye(3);T3[:2]=T
wanted=H@np.linalg.inv(T3)
s=np.linalg.norm(wanted[:2,0]);r=math.degrees(math.atan2(wanted[1,0],wanted[0,0]));center=wanted@np.array([1079/2,1071/2,1.])
e=dict(foto['encuadre']);e.update(escala=float(s/min(1080/1079,1080/1071)),x=float((center[0]-540)/1080),y=float((center[1]-540)/1080),rotacion=r)
minimal=dict(foto['encuadre'],y=.028)
def project(M,p):return (M@np.column_stack([p,np.ones(len(p))]).T).T[:,:2]
def residual(M):return np.linalg.norm(project(M,po)-project(H,ph),axis=1)
metrics={}
for label,M in [('antes',O),('solo_y_0028',place(minimal,1079,1071)),('optimo_cara',wanted)]:
    rr=residual(M);v=rr[ins];metrics[label]={'inliers':len(v),'mediana_px':float(np.median(v)),'media_px':float(v.mean()),'max_px':float(v.max()),'p95_px':float(np.percentile(v,95))}
print(json.dumps({'propuesta_encuadre':e,'minimal':minimal,'metricas':metrics},indent=2))
res={'fuente_sha256':hashlib.sha256((B/'proyecto-headbop-v8/headbop-test2xd.royer').read_bytes()).hexdigest(),'foto_id':2,'head_id':6,'metodo':'SIFT/RANSAC similitud; máscaras de rostro, excluye la mayoría del cabello. Matriz de colocación inversa exacta de Escena.matriz. Sólo encuadre foto.','matriz_head_a_original':T.tolist(),'buenas_correspondencias':len(good),'inliers':int(ins.sum()),'puntos_head':ph.tolist(),'puntos_original':po.tolist(),'inliers_bool':ins.tolist(),'antes_encuadre':foto['encuadre'],'solo_y_encuadre':minimal,'propuesta_encuadre':e,'matriz_original_a_canvas_propuesta':wanted.tolist(),'metricas_1080':metrics}
(D/'registro-cara-intro-v9.json').write_text(json.dumps(res,ensure_ascii=False,indent=2),encoding='utf-8')
# Diagnostic only: both source faces shown at output position with correspondence error.
canv=[]
for name,M in [('ANTES',O),('SOLO Y+0.028',place(minimal,1079,1071)),('AJUSTE FACIAL',wanted)]:
    f=cv2.warpAffine(o,M[:2],(1080,1080),borderValue=(246,246,246))
    hh=cv2.warpAffine(h[:,:,:3],H[:2],(1080,1080),borderValue=(0,0,0)); aa=cv2.warpAffine(h[:,:,3],H[:2],(1080,1080))/255
    mix=(f*.5+hh*.5*aa[:,:,None]+f*.5*(1-aa[:,:,None])).astype(np.uint8)
    for a,b in zip(project(M,po[ins]),project(H,ph[ins])):
        cv2.line(mix,tuple(np.rint(a).astype(int)),tuple(np.rint(b).astype(int)),(0,0,255),1)
        cv2.circle(mix,tuple(np.rint(a).astype(int)),3,(0,255,0),1)
    crop=mix[220:650,250:700]
    cv2.putText(crop,name,(8,26),cv2.FONT_HERSHEY_SIMPLEX,.7,(255,50,50),2)
    canv.append(crop)
cv2.imwrite(str(D/'registro-cara-intro-v9.jpg'),np.concatenate(canv,axis=1))
