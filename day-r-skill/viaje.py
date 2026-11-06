#!/usr/bin/env python3
"""viaje.py DESDE HASTA  -> plan de viaje por CALCULO (sin mirar el mapa). DESDE/HASTA: numero de parada, nombre parcial, o x,y (coord de ruta).
Base (probada en vivo, error <0.1 px): el personaje termina EXACTAMENTE en el punto tocado, y 'sh recentrar.sh' (lupa abrir/cerrar) lo deja en el centro (1218,540).
Entonces el siguiente toque = centro + (destino - origen)*ESC. Requiere ZOOM 3 (zoomnivel.sh estado -> T=738) y que estemos parados en DESDE al recentrar.
Si el destino cae fuera de la zona segura de toque, se parte en tramos iguales (cada uno: tap -> espera -> recentrar). Los tramos pasan por tierra libre: avisa si un punto intermedio cae cerca de otra parada.
Imprime comandos para el celular:  input tap X Y / sleep N / sh recentrar.sh   (ejecutar de a un tramo y comprobar que imprime CENTRADO)."""
import sys, json, os, math
AQUI=os.path.dirname(os.path.abspath(__file__)); K=1.5      # coord ruta -> mosaico
ESC=1.9311                                                    # px de pantalla por px de mosaico a zoom 3 (medido)
CX,CY=1218,540; X0,Y0,X1,Y1=560,200,1900,880                  # zona segura de toque (fuera: paneles/botones)
r=json.load(open(AQUI+'/zaraysk_ruta.json')); ruta=r['ruta'] if isinstance(r,dict) else r
def punto(s):
    if ',' in s: x,y=map(float,s.split(',')); return x,y,'(%g,%g)'%(x,y)
    sel=[p for p in ruta if str(p['n'])==s] or [p for p in ruta if s.lower() in p['nombre'].lower()]
    if not sel: sys.exit('NO ENCONTRADO: '+s)
    p=sel[0]; return p['x'],p['y'],'%s %s'%(p['n'],p['nombre'])
ax,ay,an=punto(sys.argv[1]); bx,by,bn=punto(sys.argv[2])
dx,dy=(bx-ax)*K*ESC,(by-ay)*K*ESC                              # desplazamiento total en px de pantalla
n=1
while not(X0<=CX+dx/n<=X1 and Y0<=CY+dy/n<=Y1): n+=1
print('# de %s  ->  %s : %.0f px de pantalla, %d tramo(s)'%(an,bn,math.hypot(dx,dy),n))
for i in range(1,n+1):
    px,py=CX+dx/n,CY+dy/n                                       # cada tramo parte del centro (recentrado)
    mx,my=(ax+(bx-ax)*i/n),(ay+(by-ay)*i/n)                     # donde queda el personaje (coord ruta)
    near=[p['n'] for p in ruta if (i<n) and math.hypot(p['x']-mx,p['y']-my)<25]
    print('input tap %d %d'%(round(px),round(py)))
    print('sleep %d'%(3+math.hypot(dx/n,dy/n)/80))
    print('sh recentrar.sh   # debe imprimir CENTRADO; el personaje queda en ruta(%.0f,%.0f)%s'%(mx,my,('  OJO: cerca de parada(s) %s'%near) if near else ''))
