import sys,json,math
from pathlib import Path
sys.path.insert(0,r'C:/Users/PC/Downloads/royer-method-work/python')
import numpy as np,cv2
B=Path(r'C:/Users/PC/Downloads/royer-method-work/referencias/test2xd-20261017/analisis')
p=B/'registro-cara-intro-v9.json';j=json.loads(p.read_text(encoding='utf-8'))
a=np.array(j['puntos_head'])[j['inliers_bool']];b=np.array(j['puntos_original'])[j['inliers_bool']]
# Exact linear least squares, four-parameter similarity (x'=ax-by+tx, y'=bx+ay+ty).
def fit(a,b):
    n=len(a);A=np.zeros((2*n,4));A[::2,0]=a[:,0];A[::2,1]=-a[:,1];A[::2,2]=1;A[1::2,0]=a[:,1];A[1::2,1]=a[:,0];A[1::2,3]=1
    q=np.linalg.lstsq(A,b.reshape(-1),rcond=None)[0]
    return np.array([[q[0],-q[1],q[2]],[q[1],q[0],q[3]]])
def proj(T,a):return np.column_stack([a,np.ones(len(a))])@T.T
errs=[]
for i in range(len(a)):
    mask=np.arange(len(a))!=i;T=fit(a[mask],b[mask]);errs.append(float(np.linalg.norm(proj(T,a[[i]])-b[[i]])*.84*1080/1079))
m=a[:,1]>970;T=fit(a[~m],b[~m]);e=np.linalg.norm(proj(T,a[m])-b[m],axis=1)*.84*1080/1079
j['validacion_fuera_del_ajuste']={'metodo':'Dejar una correspondencia fuera, volver ajustar similitud de29 restantes; todos30 puntos son únicos al píxel. Residual escalado a1080.','n':len(errs),'mediana_px':float(np.median(errs)),'media_px':float(np.mean(errs)),'max_px':float(np.max(errs)),'p95_px':float(np.percentile(errs,95)),'boca_excluida_del_ajuste':{'n':int(m.sum()),'error_px':e.tolist()},'limitacion':'El rostro está redibujado y el flequillo tiene otra silueta; no existe coincidencia exacta de todo el raster. Son correspondencias de ojos/pestañas y un punto de boca, no medición manual de nariz.'}
p.write_text(json.dumps(j,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(j['validacion_fuera_del_ajuste'],indent=2,ensure_ascii=False))
