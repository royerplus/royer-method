#!/system/bin/sh
# recoger.sh FLAGS [PAUSA] [MAX]   (v4)
# FLAGS = un caracter por objeto en el orden actual de la cuadricula (izq->der, arriba->abajo): 1 = recoger, 0 = saltar
# Regla: al recoger un objeto, todos los de su derecha se corren una casilla. El doble toque va en la casilla p = (objetos saltados antes).
# v4 (error del refri: yo mande 11 flags y la lista tenia 12 -> el ultimo objeto quedo sin recoger):
#   - ANTES de recoger cuenta los objetos de la lista (bcv quedan: textura del parche de cada casilla) y se NEGA si no coinciden con los FLAGS:
#       FLAGS_NO_COINCIDEN objetos=N flags=M   (exit 7)  -> recontar la lista y reenviar. FORZAR=1 lo ignora.
#   - Tras cada doble toque espera a que la cuadricula se estabilice (2 capturas iguales) y compara md5; si no cambio, el objeto es NO RECOGIBLE (cuenta como saltado).
#   - Al final cuenta lo que queda: si no se salto ninguno y quedan objetos reintenta (2 veces) y avisa ATASCADO_FINAL; si hay de mas: ALERTA_QUEDAN.
# DRY=1 solo imprime (sin verificar).
flags="$1"; pausa=${2:-0.12}; max=${3:-999}
X() { case $(( $1 % 8 )) in 0) echo 268;; 1) echo 436;; 2) echo 603;; 3) echo 770;; 4) echo 938;; 5) echo 1106;; 6) echo 1274;; 7) echo 1442;; esac; }
Y() { case $(( $1 / 8 )) in 0) echo 270;; 1) echo 438;; 2) echo 605;; 3) echo 773;; *) echo FUERA;; esac; }
f=/data/local/tmp/s_rec.raw
hg() { screencap $f; dd if=$f bs=9744 skip=190 count=690 2>/dev/null | md5sum | cut -c1-12; }
estable() { a=$(hg); k=0; while [ $k -lt 6 ]; do sleep 0.15; b=$(hg); [ "$a" = "$b" ] && break; a=$b; k=$((k+1)); done; echo $a; }
quedan() { sh /data/local/tmp/bcv.sh quedan </dev/null | awk '/^QUEDAN/{print $2}'; }
if [ "$DRY" != 1 ]; then
 W=$(screencap | od -An -tu4 -N4 | tr -d ' ')
 [ "$W" != "2436" ] && { echo "NO_ES_HORIZONTAL"; exit 1; }
 q0=$(quedan)
 if [ -n "$q0" ] && [ "$q0" != "${#flags}" ] && [ "$FORZAR" != 1 ]; then echo "FLAGS_NO_COINCIDEN objetos=$q0 flags=${#flags}"; exit 7; fi
 h0=$(hg)
fi
p=0; n=0; i=0; seg=0; plan=""; atascados=""
while [ $i -lt ${#flags} ]; do
 c=${flags#$(printf %.${i}s "$flags")}; c=${c%${c#?}}
 if [ "$c" = 1 ]; then
  y=$(Y $p); [ "$y" = FUERA ] && { echo "FUERA_DE_VISTA p=$p (hay que desplazar)"; break; }
  x=$(X $p)
  if [ "$DRY" = 1 ]; then plan="$plan $p"; n=$((n+1)); i=$((i+1)); continue; fi
  input tap $x $y; input tap $x $y; sleep $pausa
  h1=$(estable)
  if [ "$h1" = "$h0" ]; then
    atascados="$atascados $p"; p=$((p+1)); seg=$((seg+1)); [ $seg -ge 3 ] && { i=$((i+1)); seg=0; }; continue
  fi
  seg=0; h0=$h1; plan="$plan $p"; n=$((n+1))
  [ $n -ge $max ] && break
 else p=$((p+1)); fi
 i=$((i+1))
done
extra=""
if [ "$DRY" != 1 ]; then
 z=$(printf %s "$flags" | tr -d 1 | wc -c); at=$(echo $atascados | wc -w)
 estable >/dev/null; qd=$(quedan); [ -z "$qd" ] && qd=0
 if [ $z -eq 0 ] && [ $at -eq 0 ]; then
   r=0; while [ $qd -gt 0 ] && [ $r -lt 2 ]; do
     input tap 268 270; input tap 268 270; sleep 0.3; estable >/dev/null; q2=$(quedan); [ -z "$q2" ] && q2=$qd
     [ $q2 -lt $qd ] && n=$((n+qd-q2)); r=$((r+1)); extra="$extra reintento$r:quedaban$qd->$q2"; qd=$q2
   done
   [ $qd -gt 0 ] && extra="$extra ATASCADO_FINAL_quedan=$qd"
 else
   [ $qd -gt $((z+at)) ] && extra=" ALERTA_QUEDAN=$qd esperado<=$((z+at))"
 fi
 extra=" quedan=$qd$extra"
fi
echo "HECHO recogidas=$n casillas:$plan atascados:$atascados$extra"
