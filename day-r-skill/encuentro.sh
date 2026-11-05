#!/system/bin/sh
# encuentro.sh [max_s] -- ventana "Has visto a un enemigo" (mapa) -> deja la batalla corriendo con batallaD.sh.
# evita duplicados: mata batallas/encuentros previos (dos scripts a la vez pelean por bcv.cmd y dan NO_MUNECO en bucle)
for p in $(ps -A -o PID,ARGS | grep -E "sh (batallaD|encuentro)\.sh" | grep -v grep | awk "{print \$1}"); do [ "$p" != "$$" ] && kill $p 2>/dev/null; done
#  1. Pulsa "Batalla automatica".
#  2. Si sale el cartel "El enemigo es demasiado fuerte..." (nivel Mortal, etc.): OK -> "Ataque" -> sigue con batallaD.sh (batalla normal).
#  3. Si no sale ese cartel: Confirmar -> Cerrar (la batalla se resolvio sola). Imprime AUTO_HECHA.
# Si no hay ventana de encuentro (ya esta el pergamino / armas / combate), pasa directo a batallaD.sh.
# Imprime ademas: JUEGO_FUERA (pantalla vertical) | lo que imprima batallaD.sh.  Decisiones en enc.log.
cd /data/local/tmp
MAX=${1:-900}; t0=$(date +%s); : > enc.log
f=enc.raw
log() { echo "$(( $(date +%s)-t0 ))s $*" >> enc.log; }
cap() { screencap $f; }
# ancho de la captura (2436 = horizontal, 1080 = vertical)
ancho() { dd if=$f bs=4 count=1 2>/dev/null | od -An -tu4 | tr -d ' '; }
# R G B del pixel x y (la captura cruda tiene 16 bytes de cabecera = 4 bloques de 4)
rgb() { dd if=$f bs=4 skip=$(( 4 + $2*2436 + $1 )) count=1 2>/dev/null | od -An -tu1 | tr -s ' '; }
# Ventana de encuentro: boton Batalla automatica (tostado) y boton Ataque (verde grisaceo)
esencuentro() {
  set -- $(rgb 760 712); [ $(( $1-$2 )) -ge 20 ] && [ $(( $1-$3 )) -ge 45 ] && [ $1 -lt 190 ] || return 1
  set -- $(rgb 1000 770); [ $(( $1-$2 )) -ge 20 ] && [ $(( $1-$3 )) -ge 45 ] && [ $1 -lt 190 ] || return 1
  set -- $(rgb 1420 712); [ $(( $2-$1 )) -ge 3 ] && [ $(( $2-$3 )) -ge 30 ] && [ $1 -lt 175 ] || return 1
  set -- $(rgb 1650 770); [ $(( $2-$1 )) -ge 3 ] && [ $(( $2-$3 )) -ge 30 ] && [ $1 -lt 175 ] || return 1
  return 0
}
# Cartel de pergamino con boton OK amarillo-verdoso (R ~ G, B mucho menor)
espopup() {
  for p in "1060 630" "1370 670" "1100 675"; do
    set -- $(rgb $p); d=$(( $1-$2 )); [ $d -lt 0 ] && d=$(( -d ))
    [ $d -le 10 ] && [ $(( $1-$3 )) -ge 55 ] && [ $1 -ge 110 ] || return 1
  done
  return 0
}
[ -n "$ENC_TEST" ] && { cap; echo "ancho=$(ancho)"; esencuentro && echo ENCUENTRO; espopup && echo POPUP; exit 0; }

cap; w=$(ancho)
[ "$w" != 2436 ] && { log "pantalla no horizontal (ancho=$w)"; echo JUEGO_FUERA; exit 1; }
hay=0; i=0
while [ $i -lt 6 ]; do
  cap; esencuentro && { hay=1; break; }
  i=$((i+1)); sleep 0.5
done
if [ $hay = 1 ]; then
  log "ventana de encuentro -> Batalla automatica"
  sh bcv.sh tap 889 739 @1200 >/dev/null
  cap
  if espopup; then
    log "cartel 'demasiado fuerte' -> OK, luego Ataque (batalla normal)"
    sh bcv.sh tap 1216 652 @900 >/dev/null
    j=0; while [ $j -lt 6 ]; do cap; esencuentro && break; j=$((j+1)); sleep 0.4; done
    sh bcv.sh tap 1541 739 @1000 >/dev/null
  else
    log "la batalla automatica se acepto -> Confirmar, luego Cerrar"
    sh bcv.sh tap 1468 708 @1000 >/dev/null
    cp $f enc_auto.raw 2>/dev/null
    sh bcv.sh tap 923 842 @1000 >/dev/null
    echo AUTO_HECHA; exit 0
  fi
else
  log "no hay ventana de encuentro: sigo con la batalla"
fi
REST=$(( MAX - ($(date +%s)-t0) ))
exec sh batallaD.sh $REST
