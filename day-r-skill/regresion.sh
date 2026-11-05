#!/bin/bash
# regresion.sh -- ANTES de desplegar cualquier jar de combate: comprueba que sigue reconociendo los 3 ordenes de armas y que la habilidad 3 del muneco esta en y=871.
cd "$(dirname "$0")/t12"; export JAVA_TOOL_OPTIONS=; fallo=0
chk() { out=$(RAW=$PWD/$1 java -cp ../cls22:. T3 2>&1 | grep -v "Picked\|^#"); echo "$1: $out"; case "$out" in *"$2"*"Yh3=871"*) ;; *) echo "  *** FALLA (esperaba $2 y Yh3=871)"; fallo=1;; esac; }
chk clasico1.raw "m=3 p=1 r=2"; chk clasico2.raw "m=3 p=1 r=2"      # pistola, rifle, muneco
chk ../../v3/t/NEG_combate_ini.raw "m=1 p=3 r=2"                    # muneco, rifle, pistola
chk nuevo_dim.raw "m=1 p=2 r=3"                                      # muneco, pistola, rifle
out=$(RAW=$PWD/sin_combate.raw java -cp ../cls22:. T3 2>&1 | grep -v "Picked\|^#"); echo "sin_combate: $out"; case "$out" in INSEGURO*) ;; *) echo "  *** FALLA (en el mapa no debe decidir)"; fallo=1;; esac
for par in "muneco.raw MUNECO" "h3_lista.raw MUNECO" "muneco_pegado.raw MIO"; do set -- $par; f=$(find .. ../.. -maxdepth 2 -name $1 2>/dev/null | head -1); out=$(RAW=$PWD/$f java -cp ../cls22:. T7 2>&1 | tail -1); echo "$1: $out"; case "$out" in *"seguro=$2"*) ;; *) echo "  *** FALLA turno (esperaba $2)"; fallo=1;; esac; done
# enemigos = solo fichas con aro azul/verde (cajas/rocas/barriles NO): n enemigos esperados y casillas que NO deben ser enemigo
chkE() { out=$(RAW=$PWD/$1 java -cp ../cls22:. T9 2>&1 | grep "^ene="); n=$(echo "$out" | sed 's/ |.*//; s/^ene=//' | wc -w); echo "$1: $n enemigos"; [ "$n" = "$2" ] || { echo "  *** FALLA (esperaba $2 enemigos)"; fallo=1; }; case "$out" in *"$3"*) echo "  *** FALLA: $3 no debe ser enemigo"; fallo=1;; esac; }
chkE bandidos_cajas.raw 7 "xx"; chkE nuevo_dim.raw 6 " 3,7,"; chkE clasico1.raw 4 "xx"
# ficha clara con aro (Chupacabras, cara blanca) debe contarse: N=9, casilla 8,3
out=$(RAW=$PWD/chupacabras.raw java -cp ../cls22:. T9 9 2>&1 | grep "^ene="); echo "chupacabras.raw: $out"; case "$out" in *" 8,3,"*|*"=8,3,"*) ;; *) echo "  *** FALLA: la Chupacabras 8,3 no se detecta"; fallo=1;; esac
[ $fallo = 0 ] && echo "REGRESION OK" || echo "REGRESION CON FALLOS"; exit $fallo
