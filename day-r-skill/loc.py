#!/usr/bin/env python3
"""loc.py captura.png -> ubica la captura en el mosaico (SIFT): zoom, coordenadas del CENTRO de pantalla (1218,540) en el mosaico y la ruta restante en pantalla.
Uso: python3 -I loc.py captura.png"""
import cv2, numpy as np, sys, json, os
AQUI=os.path.dirname(os.path.abspath(__file__)); K=1.5
mos=cv2.imread(AQUI+'/zaraysk_mosaico.png',0); r=json.load(open(AQUI+'/zaraysk_ruta.json')); ruta=r['ruta'] if isinstance(r,dict) else r
sift=cv2.SIFT_create(6000); km,dm=sift.detectAndCompute(mos,None)
im=cv2.imread(sys.argv[1],0); mask=np.zeros(im.shape,np.uint8); mask[130:760,470:1800]=255
k,dd=sift.detectAndCompute(im,mask)
m=[a for a,b in cv2.BFMatcher().knnMatch(dd,dm,k=2) if a.distance<0.7*b.distance]
src=np.float32([k[a.queryIdx].pt for a in m]); dst=np.float32([km[a.trainIdx].pt for a in m])
M,inl=cv2.estimateAffinePartial2D(src,dst,method=cv2.RANSAC,ransacReprojThreshold=4)
A=np.vstack([M,[0,0,1]]); s=np.hypot(M[0,0],M[1,0]); Ai=np.linalg.inv(A)
c=A@np.array([1218,540,1.0])
print('inliers',int(inl.sum()),'px_pantalla_por_px_mosaico %.4f'%(1/s),'centro_en_mosaico (%.1f,%.1f)'%(c[0],c[1]),'(coord ruta: %.1f,%.1f)'%(c[0]/K,c[1]/K))
for p in ruta:
    q=Ai@np.array([p['x']*K,p['y']*K,1.0]); print(p['n'],p['nombre'][:30],'pantalla (%d,%d)'%(q[0],q[1]))
