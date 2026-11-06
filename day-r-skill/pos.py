#!/usr/bin/env python3
"""pos.py captura.png [DESTINO]  -- VERIFICACION DE POSICION (un screenshot, ~2 s) y, si hay destino, el toque que falta para llegar.
1) Ubica la captura en el mosaico (SIFT) -> zoom/escala. 2) Encuentra la ficha del personaje (aro, plantilla ficha_personaje.png; solo se ve a zoom nivel 3 o mas lejos) y de ahi su posicion en coord de ruta,
   SIN necesidad de recentrar con la lupa (sirve tras un ataque skipeable, un empujon, o si Royer movio algo).
3) Con DESTINO (n de parada, nombre o x,y): imprime LLEGADO si el error es < 2.5 unidades de ruta; si no, el toque (dentro de la zona segura, partido en tramos si hace falta) y la espera.
Bucle recomendado (todo desde el celular salvo esta cuenta): screenshot -> pos.py -> input tap -> sleep -> repetir hasta LLEGADO. La parada solo cuenta como hecha al imprimir LLEGADO (en zona enemiga el personaje puede quedar fuera del punto exacto).
Salida de codigo: 0 ok, 2 sin ficha/pocas coincidencias."""
import cv2, numpy as np, sys, json, os, math
AQUI=os.path.dirname(os.path.abspath(__file__)); K=1.5; ESC0=0.9194; UMBRAL=2.5
X0,Y0,X1,Y1=560,200,1900,880
mos=cv2.imread(AQUI+'/zaraysk_mosaico.png',0); r=json.load(open(AQUI+'/zaraysk_ruta.json')); ruta=r['ruta'] if isinstance(r,dict) else r
sift=cv2.SIFT_create(6000); km,dm=sift.detectAndCompute(mos,None)
im=cv2.imread(sys.argv[1],0); mask=np.zeros(im.shape,np.uint8); mask[130:760,470:1800]=255
k,dd=sift.detectAndCompute(im,mask)
m=[a for a,b in cv2.BFMatcher().knnMatch(dd,dm,k=2) if a.distance<0.75*b.distance]
if len(m)<15: print('SIN_UBICACION: pocas coincidencias (%d)'%len(m)); sys.exit(2)
src=np.float32([k[a.queryIdx].pt for a in m]); dst=np.float32([km[a.trainIdx].pt for a in m])
M,inl=cv2.estimateAffinePartial2D(src,dst,method=cv2.RANSAC,ransacReprojThreshold=4)
if inl.sum()<15: print('SIN_UBICACION: pocas coincidencias buenas (%d)'%inl.sum()); sys.exit(2)
A=np.vstack([M,[0,0,1]]); Ai=np.linalg.inv(A); esc=1/np.hypot(M[0,0],M[1,0])
t=cv2.cvtColor(cv2.imread(AQUI+'/ficha_personaje.png'),cv2.COLOR_BGR2GRAY)
res=cv2.matchTemplate(im,t,cv2.TM_CCOEFF_NORMED); _,sc,_,loc=cv2.minMaxLoc(res)
if sc<0.6: print('SIN_FICHA: no veo al personaje (score %.2f); ponerse a zoom nivel 3 o recentrar con la lupa'%sc); sys.exit(2)
bx,by=loc[0]+t.shape[1]/2-2,loc[1]+t.shape[0]/2         # punto de apoyo (la ficha esta 2 px a la derecha del centro)
p=(A@np.array([bx,by,1.0]))[:2]/K
print('POSICION ruta (%.1f,%.1f)  ficha en pantalla (%d,%d)  escala %.4f  coincidencias %d  ficha %.2f'%(p[0],p[1],bx,by,esc,inl.sum(),sc))
if len(sys.argv)<3: sys.exit(0)
d=sys.argv[2]
if ',' in d: dx,dy=map(float,d.split(',')); nom='(%g,%g)'%(dx,dy)
else:
    sel=[q for q in ruta if str(q['n'])==d] or [q for q in ruta if d.lower() in q['nombre'].lower()]
    if not sel: sys.exit('DESTINO no encontrado')
    dx,dy,nom=sel[0]['x'],sel[0]['y'],'%s %s'%(sel[0]['n'],sel[0]['nombre'])
err=math.hypot(dx-p[0],dy-p[1])
# RUTEO SEGURO: evitar sitios enemigos (tipo E: campamentos, hogueras, crater) con margen RADIO; se avanza por paradas V visitables como waypoints
RADIO=50
E=[(q['x'],q['y']) for q in ruta if q.get('tipo')=='E' and math.hypot(q['x']-dx,q['y']-dy)>1]   # el propio destino enemigo NO se evita (se va a el a proposito)
def dseg(a,b,c):   # distancia del punto c al segmento ab
    ax,ay=a;bx2,by2=b;cx,cy=c;vx,vy=bx2-ax,by2-ay;L=vx*vx+vy*vy
    t=0 if L==0 else max(0,min(1,((cx-ax)*vx+(cy-ay)*vy)/L));return math.hypot(ax+t*vx-cx,ay+t*vy-cy)
def libre(a,b): return all(dseg(a,b,e)>RADIO for e in E)
nodos=[(p[0],p[1],'aqui')]+[(q['x'],q['y'],str(q['n'])) for q in ruta if q.get('tipo')!='E' and math.hypot(q['x']-dx,q['y']-dy)>1]+[(dx,dy,'destino')]
import heapq
dist={0:0};prev={};pq=[(0,0)];fin=len(nodos)-1
while pq:
    d0,u=heapq.heappop(pq)
    if d0>dist.get(u,1e9): continue
    if u==fin: break
    for v in range(len(nodos)):
        if v==u: continue
        a,b=nodos[u][:2],nodos[v][:2]
        if math.hypot(a[0]-b[0],a[1]-b[1])>500 or not libre(a,b): continue
        nd=d0+math.hypot(a[0]-b[0],a[1]-b[1])
        if nd<dist.get(v,1e9): dist[v]=nd;prev[v]=u;heapq.heappush(pq,(nd,v))
if fin not in prev and fin!=0: print('SIN_RUTA_SEGURA: no hay camino evitando sitios enemigos; ir a mano / poner waypoints'); sys.exit(3)
cam=[fin]
while cam[-1]!=0: cam.append(prev[cam[-1]])
cam=cam[::-1]
print('RUTA SEGURA: '+' -> '.join(nodos[i][2] for i in cam))
if len(cam)>2:
    dx,dy=nodos[cam[1]][:2]            # primer waypoint
    err=math.hypot(dx-p[0],dy-p[1])
if math.hypot(dx-p[0],dy-p[1])<UMBRAL and len(cam)<=2: print('LLEGADO a %s (error %.1f)'%(nom,err)); sys.exit(0)
vx,vy=(dx-p[0])*K*esc,(dy-p[1])*K*esc                    # vector en px de pantalla desde la ficha
f=1.0
while True:
    tx,ty=bx+vx*f,by+vy*f
    if X0<=tx<=X1 and Y0<=ty<=Y1: break
    f-=0.02
    if f<=0.05: tx,ty=bx,by; break
print('FALTA %s: error %.1f unidades (%.0f px); %s'%(nom,err,math.hypot(vx,vy),'1 toque' if f>=0.99 else 'tramo parcial %.0f%%'%(f*100)))
print('input tap %d %d'%(round(tx),round(ty)))
print('sleep %d'%(3+math.hypot(vx*f,vy*f)/80))
