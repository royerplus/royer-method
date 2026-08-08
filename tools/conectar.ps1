# Conecta con el telefono sin tener que buscar el puerto a mano.
#
# El puerto de la depuracion inalambrica cambia cada vez que se apaga y se
# enciende, por eso habia que ir preguntandolo. Este script prueba tres vias
# en orden, de la mas estable a la menos:
#
#   1. Puerto fijo 5555, que queda puesto con "adb tcpip 5555" y aguanta
#      hasta que se reinicie el telefono.
#   2. Descubrimiento por mDNS: adb anuncia el dispositivo con un nombre
#      estable que no depende del puerto.
#   3. El puerto que se le pase como argumento, para cuando falla todo.
#
# Uso:
#   .\tools\conectar.ps1
#   .\tools\conectar.ps1 -Puerto 36869      (si hubo que reiniciar el movil)
#   .\tools\conectar.ps1 -Fijar             (vuelve a dejar el 5555 puesto)

param(
    [string]$Ip = "192.168.1.68",
    [int]$Puerto = 0,
    [switch]$Fijar
)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { Write-Error "No encuentro adb en $adb"; exit 1 }

function Conectado {
    $l = & $adb devices 2>&1 | Select-String -Pattern "\sdevice$"
    return [bool]$l
}

# --- 1. puerto fijo ---
& $adb connect "${Ip}:5555" 2>&1 | Out-Null
if (Conectado) {
    Write-Host "Conectado por puerto fijo 5555" -ForegroundColor Green
    & $adb devices -l
    exit 0
}

# --- 2. mDNS ---
$m = & $adb mdns services 2>&1 | Out-String
$p = ([regex]::Match($m, "_adb-tls-connect\._tcp\s+$([regex]::Escape($Ip)):(\d+)")).Groups[1].Value
if ($p) {
    & $adb connect "${Ip}:$p" 2>&1 | Out-Null
    if (Conectado) {
        Write-Host "Conectado por mDNS, puerto $p" -ForegroundColor Green
        if ($Fijar) {
            & $adb -s "${Ip}:$p" tcpip 5555 | Out-Null
            Start-Sleep -Seconds 4
            & $adb connect "${Ip}:5555" | Out-Null
            Write-Host "Puerto fijo 5555 dejado puesto" -ForegroundColor Green
        }
        & $adb devices -l
        exit 0
    }
}

# --- 3. puerto a mano ---
if ($Puerto -gt 0) {
    & $adb connect "${Ip}:$Puerto" 2>&1 | Out-Null
    if (Conectado) {
        Write-Host "Conectado por el puerto indicado $Puerto" -ForegroundColor Green
        & $adb -s "${Ip}:$Puerto" tcpip 5555 | Out-Null
        Start-Sleep -Seconds 4
        & $adb connect "${Ip}:5555" | Out-Null
        Write-Host "Y ademas dejo puesto el 5555 para la proxima" -ForegroundColor Green
        & $adb devices -l
        exit 0
    }
}

Write-Host "No hay manera. Comprueba que:" -ForegroundColor Yellow
Write-Host "  - la pantalla del telefono este encendida"
Write-Host "  - la depuracion inalambrica siga activada"
Write-Host "  - el telefono siga en la misma red wifi"
Write-Host "Si reiniciaste el movil, el 5555 se pierde: pasame el puerto nuevo"
Write-Host "y lo vuelvo a dejar fijo."
exit 1
