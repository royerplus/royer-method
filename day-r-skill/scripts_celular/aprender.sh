#!/system/bin/sh
# aprender.sh ID [ESC] -- guarda la referencia de la parada ID (vista actual, centrada tras la lupa). Usar SOLO cuando Claude verifico con pos.py que estamos en ID.
cd /data/local/tmp
ESC=${2:-$(cat esc_now 2>/dev/null)}; [ -z "$ESC" ] && ESC=0.92
sh recentrar.sh >/dev/null 2>&1
sh bcv.sh refsave "$1" "$ESC" | grep -v '^#'
echo "$1" > cur_pos; echo "$ESC" > esc_now
