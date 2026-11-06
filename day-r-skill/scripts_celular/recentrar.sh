#!/system/bin/sh
# recentrar.sh -- abre la lupa y la cierra: la camara queda CENTRADA en el personaje (1218,540). Solo cierra si la lupa se abrio de verdad
# (el boton de cerrar esta junto al de pausa). Imprime CENTRADO | BATALLA | SIN_LUPA:<estado>
cd /data/local/tmp
input tap 1857 1011; sleep 1.5
e=$(sh estado.sh 2>/dev/null | tail -1)
case "$e" in
  LUPA|AGOTADO|PUERTA) input tap 2257 160; sleep 1.5; echo CENTRADO;;
  BATALLA) echo BATALLA;;
  *) echo "SIN_LUPA:$e";;
esac
