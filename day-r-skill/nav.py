#!/usr/bin/env python3
"""nav.py captura.png DESTINO  -> comandos para ir al edificio DESTINO (indice de la ruta o nombre parcial) SIN mirar el mapa.
Ubica la captura (zoom y camara cualesquiera) dentro del mosaico con SIFT, convierte las coordenadas del destino (mosaico) a pixeles de pantalla,
y si queda fuera de la zona segura (HUD/paneles) manda mover la camara primero.  Imprime lineas ejecutables en el celular:
   sh camara.sh DX DY     (solo si hace falta)
   input tap X Y
   ESPERA N               (segundos aprox. de caminata)
Uso:  python3 -I nav.py captura.png 3        |  python3 -I nav.py captura.png galpon       |  python3 -I nav.py captura.png --lista"""
import cv2, numpy as np, sys, json, os
AQUI=os.path.dirname(os.path.abspath(__file__))
MOS=AQUI+'/zaraysk_mosaico.png'; RUTA=AQUI+'/zaraysk_ruta.json'; K=1.5          # vista (2/3) -> mosaico
SEGURA=(500,150,1980,930)    # x0,y0,x1,y1 en px reales: fuera quedan paneles izquierdos, botones derechos, barra inferior y reloj
CX,CY=1218,540
r=json.load(open(RUTA)); ruta=r['ruta'] if isinstance(r,dict) else r
if sys.argv[2]=='--lista':
    for p in ruta: print(p['n'],p['tipo'],p['nombre'],(round(p['x']*K),round(p['y']*K))); sys.exit()
d=sys.argv[2]
sel=[p for p in ruta if str(p['n'])==d] or [p for p in ruta if d.lower() in p['nombre'].lower()]
if not sel: sys.exit('DESTINO no encontrado')
t=sel[0]; tm=np.array([t['x']*K,t['y']*K,1.0])
mos=cv2.imread(MOS,0); sift=cv2.SIFT_create(6000)
km,dm=sift.detectAndCompute(mos,None)
im=cv2.imread(sys.argv[1],0); mask=np.zeros(im.shape,np.uint8); mask[130:760,470:1800]=255
k,dd=sift.detectAndCompute(im,mask)
m=[a for a,b in cv2.BFMatcher().knnMatch(dd,dm,k=2) if a.distance<0.7*b.distance]
if len(m)<15: sys.exit('POCAS_COINCIDENCIAS (%d): captura mala o fuera de la ciudad'%len(m))
src=np.float32([k[a.queryIdx].pt for a in m]); dst=np.float32([km[a.trainIdx].pt for a in m])
M,inl=cv2.estimateAffinePartial2D(src,dst,method=cv2.RANSAC,ransacReprojThreshold=4)
if inl.sum()<12: sys.exit('POCAS_COINCIDENCIAS_BUENAS (%d)'%inl.sum())
A=np.vstack([M,[0,0,1]]); s=np.hypot(M[0,0],M[1,0])   # pantalla->mosaico, s = mosaico/pantalla
p=np.linalg.inv(A)@tm; x,y=p[0],p[1]
x0,y0,x1,y1=SEGURA
print('# coincidencias',int(inl.sum()),'zoom (px pantalla por px mosaico) %.3f'%(1/s),'destino:',t['n'],t['nombre'],'en pantalla (%d,%d)'%(x,y))
if not(x0<=x<=x1 and y0<=y<=y1):
    dx,dy=int(round(x-CX)),int(round(y-CY))
    print('sh camara.sh %d %d'%(dx,dy)); x,y=CX,CY       # tras mover la camara el destino queda en el centro
dist=np.hypot(*(p[:2]-np.array([CX,CY])))*s
print('input tap %d %d'%(round(x),round(y)))
print('ESPERA %d'%(3+dist/90))
