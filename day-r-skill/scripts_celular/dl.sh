#!/system/bin/sh
# dl.sh URL DESTINO -- descarga un archivo (GitHub raw, repo publico) al celular. Requiere dl.jar. Ej: sh dl.sh https://raw.githubusercontent.com/royerplus/royer-method/skill-dayr-combate-v20/day-r-skill/scripts_celular/viajar.sh viajar.sh
cd /data/local/tmp
. /data/local/tmp/androidenv.sh
CLASSPATH=/data/local/tmp/dl.jar app_process /system/bin Dl "$1" "$2" 2>&1
