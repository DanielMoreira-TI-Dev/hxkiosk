# Conecta o PC ao tablet hxkiosk em modo de teste.
# Uso:
#   .\conectar-tablet.ps1
#   .\conectar-tablet.ps1 -Ip 192.168.0.50
#   .\conectar-tablet.ps1 -Adb
param(
    [string]$Ip = "",
    [int]$Port = 8787,
    [switch]$Adb
)

$ErrorActionPreference = "Stop"

function Get-TabletIpFromAdb {
    $line = adb shell ip -f inet addr show wlan0 2>$null | Select-String -Pattern "inet "
    if (-not $line) {
        return ""
    }
    if ($line.Line -match "inet (\d+\.\d+\.\d+\.\d+)") {
        return $Matches[1]
    }
    return ""
}

if ([string]::IsNullOrWhiteSpace($Ip) -and (Get-Command adb -ErrorAction SilentlyContinue)) {
    $Ip = Get-TabletIpFromAdb
}

if ($Adb) {
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        Write-Error "ADB nao encontrado no PATH."
    }
    Write-Host "Dispositivos ADB:"
    adb devices
    if (Get-Command scrcpy -ErrorAction SilentlyContinue) {
        Write-Host "Abrindo espelhamento completo com scrcpy..."
        scrcpy
    } else {
        Write-Host "scrcpy nao encontrado. Instalacao sugerida: winget install Genymobile.scrcpy"
        Write-Host "Enquanto isso, use o console web do hxkiosk."
    }
}

if ([string]::IsNullOrWhiteSpace($Ip)) {
    Write-Host "Informe o IP do tablet (painel admin do hxkiosk) ou conecte o ADB."
    $Ip = Read-Host "IP do tablet"
}

$remoteUrl = "http://${Ip}:${Port}/"
Write-Host "Abrindo console remoto de teste: $remoteUrl"
Start-Process $remoteUrl
