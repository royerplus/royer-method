#!/system/bin/sh
# batallaD.sh [max_s] -- CICLO COMPLETO de combate/mision, RAPIDO: toda la vision y los toques los hace el servicio Java (bcv.sh -> Bcv.java), una captura por decision.
# Mismas reglas que batallaC.sh (ese queda como respaldo):
#  - Pergamino: 1a opcion; si el click no avanza (pantalla igual/ya vista) -> clickea las demas en fila; >60 s en la ventana: termina. Delay 0.5 s. Cartel PELIGRO: nada. Armas: "Empezar batalla".
#  - TURNO 1 (muneco libre en el 1er turno): invocar y pasar, SIN disparar ni mover. Muneco muerto: reinvocar y seguir con el rifle.
#  - Mi turno: rifle a los enemigos en rango MIENTRAS haya puntos de accion (>=2) y usos; cambia de blanco solo. Nadie a tiro: UN movimiento hacia el mas cercano. Sin disparos posibles: pasar.
#  - Turno del muneco: habilidad 3 al mejor enemigo (si no esta en enfriamiento, nada) y pasar. Cada pase se verifica (reintenta).
#  - Victoria / Mision cumplida -> Cerrar. Nunca reabre el juego: si sale de pantalla >20 s termina.
# Imprime: FIN_BATALLA | SIN_VENTANA | TIEMPO | JUEGO_FUERA | INVOCACION_FALLA | PERGAMINO_ATASCADO | PANTALLA_DESCONOCIDA.  Decisiones en bc.log.
cd /data/local/tmp
MAX=${1:-900}; t0=$(date +%s); : > bc.log; rm -f n_tablero jug_pos
log() { echo "$(( $(date +%s)-t0 ))s $*" >> bc.log; }
B() { sh bcv.sh "$@"; }
PRIMER=0; FIN=0; VIO=0; COMB=0; NADA=0; PT0=0; FALLA=0; SEEN=; PRIM_CHK=0; INVF=0; FUERA=0
visto() { case " $SEEN " in *" $1 "*) return 0;; esac; return 1; }
campo() { echo "$1" | sed -n "s/^$2=//p"; }
# Ventana "Has visto a un enemigo" (batalla) visible? (misma deteccion que encuentro.sh, modo ENC_TEST)
hay_encuentro() { ENC_TEST=1 sh encuentro.sh 2>/dev/null | grep -q ENCUENTRO; }
# Si en medio de un evento sale la ventana de enemigo: se resuelve con encuentro.sh (Batalla automatica / Ataque) y se sigue con la batalla
si_encuentro() { hay_encuentro && { log "ventana de enemigo durante el evento: la resuelvo con encuentro.sh"; exec sh encuentro.sh $(( MAX-($(date +%s)-t0) )); }; }
# RONDA: el evento no llega a batalla -> probar las demas opciones visibles (filas 2 y 3; la 1a ya se probo), UN click a cada una,
# comprobando ANTES de cada click que no ha empezado la ventana de batalla, y DESPUES de cada click si cambio la pantalla.
ronda() {
  for y in 747 864; do
    si_encuentro
    log "pergamino (ronda: no llega a batalla) -> opcion y=$y"; B tap 877 $y @500 >/dev/null
    v3=$(B ver); [ "$(campo "$v3" PANT)" = PERGAMINO ] || return 0
    si_encuentro
  done
}
pergamino() {
  [ $PT0 = 0 ] && { PT0=$(date +%s); SEEN=; PCL=0; }
  [ $(( $(date +%s)-PT0 )) -ge 90 ] && { log "pergamino atascado >90 s: termino"; screencap pergamino_atascado.raw; echo PERGAMINO_ATASCADO; exit 3; }
  SEEN="$SEEN $HASH"
  si_encuentro
  if [ $FALLA = 0 ]; then
    log "pergamino -> 1a opcion"; B tap 877 630 @500 >/dev/null; PCL=$((PCL+1))
    v2=$(B ver); [ "$(campo "$v2" PANT)" = PERGAMINO ] || return 0
    h1=$(campo "$v2" HASH); visto "$h1" && { FALLA=1; log "el click no avanzo (pantalla igual o ya vista): clickeo las demas opciones"; }
    # nuevo: aunque cada click abra una pagina nueva, si tras PMAX1 clicks de la 1a opcion no hay batalla -> ronda con las demas opciones
    [ $FIN = 0 ] && [ $PCL -ge ${PMAX1:-8} ] && { PCL=0; ronda; }   # solo mientras NO haya habido batalla (FIN=0): tras ganar, no se prueban otras opciones
  else
    for y in 756 883; do
      si_encuentro
      log "pergamino (modo fila) -> opcion y=$y"; B tap 877 $y @500 >/dev/null
      v2=$(B ver); [ "$(campo "$v2" PANT)" = PERGAMINO ] || return 0
      h1=$(campo "$v2" HASH); visto "$h1" || { FALLA=0; log "avanzo: vuelvo a la 1a opcion"; return 0; }
    done
  fi
}
while [ $(( $(date +%s)-t0 )) -lt $MAX ]; do
  v=$(B ver)
  case "$v" in SERVICIO*) log "$v"; echo "$v"; exit 4;; esac
  PANT=; TURNO=; ALIADO=; AP=; HASH=; eval "$v" 2>/dev/null
  # SEGURIDAD: si el juego no tiene el foco (p. ej. la app de Claude encima) NO tocar nada: tratarlo como VERTICAL
  case "$(dumpsys window 2>/dev/null | grep mCurrentFocus)" in *dayrpremium*) ;; *) PANT=VERTICAL;; esac
  if [ "$PANT" = VERTICAL ]; then
    [ $FUERA = 0 ] && FUERA=$(date +%s)
    [ $(( $(date +%s)-FUERA )) -ge 20 ] && { log "juego fuera de pantalla mas de 20 s: termino"; echo JUEGO_FUERA; exit 1; }
    sleep 1; continue
  fi
  FUERA=0
  [ "$PANT" != PERGAMINO ] && { PT0=0; FALLA=0; }
  case "$PANT" in
    VICTORIA) log "VICTORIA -> Cerrar"; B tap 923 843 @800 >/dev/null; FIN=1; NADA=0;;
    MISION) log "MISION CUMPLIDA -> Cerrar"; B tap 1011 812 @800 >/dev/null; FIN=1; NADA=0;;
    PERGAMINO) pergamino; NADA=0; VIO=1;;
    ARMAS) log "armas -> Empezar batalla (turno 1 a la vista)"; B tap 1848 894 @1000 >/dev/null; COMB=1; PRIMER=1; NADA=0;;
    COMBATE)
      case "$TURNO" in
        MIO)
          COMB=1; NADA=0
          if [ $PRIM_CHK = 0 ]; then PRIM_CHK=1; [ "$ALIADO" = LIBRE ] && { PRIMER=1; log "primer turno visto con el muneco libre: lo trato como TURNO 1 (sin disparar ni mover)"; }; fi
          r=$(B turno_mio $PRIMER | grep -v '^#' | tr '\n' ' '); log "turno MIO -> $r"
          case "$r" in
            *SLOTS_DESCONOCIDOS*) log "no reconozco las armas por su icono: me detengo"; echo SLOTS_DESCONOCIDOS; exit 6;;
            *INVOCACION_FALLA*) INVF=$((INVF+1)); [ $INVF -ge 3 ] && { echo INVOCACION_FALLA; exit 2; };;
            *PASE*) PRIMER=0;;
          esac;;
        MUNECO) COMB=1; NADA=0; r=$(B turno_muneco | grep -v '^#' | tr '\n' ' '); log "turno MUNECO -> $r"; case "$r" in *SLOTS_DESCONOCIDOS*) echo SLOTS_DESCONOCIDOS; exit 6;; esac;;
        *) sleep 0.2;;
      esac;;
    *)
      NADA=$((NADA+1))
      [ $FIN = 1 ] && [ $NADA -ge 5 ] && { log "fin"; echo FIN_BATALLA; exit 0; }
      [ $COMB = 0 ] && [ $VIO = 0 ] && [ $FIN = 0 ] && [ $NADA -ge 4 ] && { echo SIN_VENTANA; exit 0; }
      [ $VIO = 1 ] && [ $FIN = 0 ] && [ $NADA -ge 40 ] && { log "pantalla no reconocida durante mucho rato: guardo captura y termino"; screencap pantalla_rara.raw; echo PANTALLA_DESCONOCIDA; exit 5; }
      sleep 0.5;;
  esac
done
echo TIEMPO
