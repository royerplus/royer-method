#!/system/bin/sh
# viajar.sh DESTINO [DESDE]  -- viaje A SCRIPT entre paradas de la ciudad (ruta.txt), con verificacion de posicion y reposicion. NO luteo.
#  DESTINO/DESDE = numero de parada (ruta.txt) o "x,y" (coord de ruta).  DESDE por defecto = cur_pos (la ultima parada verificada).
# Requiere: zoom 3 (zoomnivel.sh 3), juego horizontal y al frente, bcv.jar con Pos (refsave/pos/loc/ruta/paso), ruta.txt, recentrar.sh.
# Por cada tramo: tap -> sleep -> recentrar (lupa) -> verifica contra la referencia (ref_ID.bin) y corrige hasta error < 2.5 unidades.
# Interrupciones (ventana de enemigo, combate, pergamino): resuelve con encuentro.sh y se RELOCALIZA (bcv loc) antes de seguir; nunca luteo en medio.
# Imprime al final:  LLEGADO ID | LLEGADO_SIN_REF ID (llegue pero sin referencia para verificar) | PERDIDO (no se donde estoy) | SIN_RUTA | JUEGO_FUERA | NO_CONVERGE ID
# Referencias: tras llegar a una parada que Claude verifico con pos.py -> `sh aprender.sh ID ESC`.
cd /data/local/tmp
DEST="$1"; FROM="${2:-$(cat cur_pos 2>/dev/null)}"
B() { sh bcv.sh "$@" </dev/null | grep -v '^#'; }
log() { echo "$(date +%T) $*" >> viajar.log; }
fuera() { case "$(dumpsys window 2>/dev/null | grep mCurrentFocus)" in *dayrpremium*) return 1;; *) return 0;; esac; }
xy() { awk -v i="$1" '$1==i {print $2, $3}' ruta.txt; }       # coordenadas de una parada
[ -z "$FROM" ] && { echo PERDIDO; exit 5; }
: > viajar.log
fuera && { echo JUEGO_FUERA; exit 1; }
ESC=$(cat esc_now 2>/dev/null); [ -z "$ESC" ] && ESC=0.92
case "$FROM" in *,*) CX=${FROM%,*}; CY=${FROM#*,};; *) set -- $(xy $FROM); CX=$1; CY=$2;; esac
[ -z "$CX" ] && { echo PERDIDO; exit 5; }
interrupcion() {   # devuelve 0 si habia algo que resolver
  v=$(B ver); PANT=; eval "$v" 2>/dev/null
  if [ "$PANT" = COMBATE ] || [ "$PANT" = ARMAS ] || [ "$PANT" = PERGAMINO ] || ENC_TEST=1 sh encuentro.sh 2>/dev/null | grep -q ENCUENTRO; then
    log "interrupcion ($PANT): encuentro.sh"; sh encuentro.sh 600 >/dev/null 2>&1; sleep 1; sh recentrar.sh >/dev/null 2>&1
    l=$(B loc); log "relocalizo: $l"
    set -- $l; [ "$1" = LOC ] || { echo PERDIDO; exit 5; }
    n=$2; cur=$(xy $n); set -- $cur; CX=$1; CY=$2          # parada reconocida (el desvio se corrige en el siguiente tramo)
    return 0
  fi; return 1
}
ruta=$(B ruta "$FROM" "$DEST")
case "$ruta" in H*) ;; *) log "ruta: $ruta"; echo SIN_RUTA; exit 4;; esac
echo "$ruta" | sed -n '2,$p' > /data/local/tmp/viajar.hops
while read h id hx hy <&3; do
  tries=0
  while :; do
    fuera && { echo JUEGO_FUERA; exit 1; }
    interrupcion
    p=$(B paso $CX $CY $hx $hy $ESC); log "tramo a $id: $p"
    set -- $p; TX=$2; TY=$3; SL=$5; F=$7
    input tap $TX $TY; sleep $SL; sh recentrar.sh >/dev/null 2>&1
    if [ "$F" = "1.00" ]; then CX=$hx; CY=$hy; break; fi
    CX=$(awk -v a=$CX -v b=$hx -v f=$F 'BEGIN{printf "%.1f", a+(b-a)*f}'); CY=$(awk -v a=$CY -v b=$hy -v f=$F 'BEGIN{printf "%.1f", a+(b-a)*f}')
    tries=$((tries+1)); [ $tries -ge 8 ] && { echo NO_CONVERGE $id; exit 6; }
  done
  # verificar contra la referencia de esta parada y corregir hasta quedar bien
  if [ -f ref_$id.bin ]; then
    k=0; ok=0
    while [ $k -lt 5 ]; do
      m=$(B pos $id); log "pos $id: $m"; set -- $m
      [ "$1" = POS ] || break
      DX=$2; DY=$3; SC=$4; ESCN=$6
      [ "$(awk -v s=$SC 'BEGIN{print (s<0.40)}')" = 1 ] && { log "score bajo ($SC): no verifico"; break; }
      echo $ESCN > esc_now; ESC=$ESCN
      err=$(awk -v x=$DX -v y=$DY -v e=$ESC 'BEGIN{printf "%.1f", sqrt(x*x+y*y)/(1.5*e)}')
      [ "$(awk -v e=$err 'BEGIN{print (e<2.5)}')" = 1 ] && { ok=1; break; }
      input tap $((1218+DX)) $((540+DY)); sleep 3; sh recentrar.sh >/dev/null 2>&1; k=$((k+1))
    done
    [ $ok = 1 ] && { CX=$hx; CY=$hy; log "verificado $id"; } || log "SIN_VERIFICAR $id"
  fi
  echo "$CX $CY $id" > cur_pos_xy
done 3< /data/local/tmp/viajar.hops
ULT=$(tail -1 viajar.hops | awk '{print $2}')
echo "$ULT" > cur_pos
[ -f ref_$ULT.bin ] && echo "LLEGADO $ULT" || echo "LLEGADO_SIN_REF $ULT"
