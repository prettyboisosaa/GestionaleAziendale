# Avvia il backend in locale, dopo essersi assicurato che il database sia
# pronto ad accettare connessioni.
#
# Due problemi che questo script risolve:
#
# 1. Il wrapper Maven (mvnw) ha bisogno di JAVA_HOME. Su questa macchina non
#    e' impostato e `java` non sta nel PATH, quindi `.\mvnw spring-boot:run`
#    muore subito con "The JAVA_HOME environment variable is not defined
#    correctly". Lo script cerca un JDK e lo imposta solo per questo comando.
#
# 2. Se MySQL non e' ancora in ascolto, Flyway non riesce a connettersi e
#    l'avvio si interrompe con "Unable to obtain connection from database:
#    Communications link failure". Lo script avvia il container del database
#    e aspetta che lo healthcheck lo dia per sano.
#
# Uso:  .\avvia.ps1

$ErrorActionPreference = 'Stop'
$radiceProgetto = Split-Path $PSScriptRoot -Parent

function Trova-Jdk {
    # Se JAVA_HOME e' gia' valido, si usa quello.
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return $env:JAVA_HOME
    }
    # JDK installati dall'IDE o a mano, dal piu' recente.
    $radici = @(
        (Join-Path $env:USERPROFILE '.jdks'),
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Java',
        'C:\Program Files\Microsoft'
    )
    foreach ($radice in $radici) {
        if (-not (Test-Path $radice)) { continue }
        $trovato = Get-ChildItem -Path $radice -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } |
            Sort-Object Name -Descending |
            Select-Object -First 1
        if ($trovato) { return $trovato.FullName }
    }
    # java nel PATH: si risale alla cartella del JDK.
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($java) { return (Split-Path (Split-Path $java.Source -Parent) -Parent) }
    return $null
}

function Attendi-Database {
    param([int]$SecondiMax = 90)

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Host "docker non trovato: salto il controllo del database." -ForegroundColor Yellow
        return
    }

    $stato = (docker inspect gestionale-db --format '{{.State.Status}}' 2>$null)
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Avvio dei servizi di sviluppo (db, phpmyadmin)..." -ForegroundColor Cyan
        docker compose -f (Join-Path $radiceProgetto 'docker-compose.yml') up -d db phpmyadmin
    }
    elseif ($stato -ne 'running') {
        Write-Host "Il container gestionale-db e' in stato '$stato': lo avvio..." -ForegroundColor Cyan
        docker start gestionale-db | Out-Null
    }

    Write-Host "Attendo che MySQL sia pronto..." -NoNewline -ForegroundColor Cyan
    $scaduto = (Get-Date).AddSeconds($SecondiMax)
    while ((Get-Date) -lt $scaduto) {
        $salute = (docker inspect gestionale-db --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}assente{{end}}' 2>$null)
        if ($salute -eq 'healthy') { Write-Host " pronto." -ForegroundColor Green; return }
        if ($salute -eq 'assente') { Write-Host " nessun healthcheck, proseguo." -ForegroundColor Yellow; return }
        Write-Host "." -NoNewline
        Start-Sleep -Seconds 2
    }
    Write-Host ""
    Write-Error "MySQL non e' diventato sano entro $SecondiMax secondi. Controlla: docker logs gestionale-db"
    exit 1
}

$jdk = Trova-Jdk
if (-not $jdk) {
    Write-Error "Nessun JDK trovato. Installane uno (serve Java 25 o superiore) oppure imposta JAVA_HOME."
    exit 1
}
$env:JAVA_HOME = $jdk
Write-Host "JAVA_HOME = $jdk" -ForegroundColor Cyan

Attendi-Database

& (Join-Path $PSScriptRoot 'mvnw.cmd') spring-boot:run
exit $LASTEXITCODE
