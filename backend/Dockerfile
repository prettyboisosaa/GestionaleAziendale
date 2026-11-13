# --- Fase di build -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Il pom da solo, e le dipendenze scaricate prima del codice: finche' il pom
# non cambia, Docker riusa questo livello e un build dopo una modifica al
# codice non riscarica mezzo Maven Central. Prima il `COPY src` veniva subito
# dopo il pom, quindi ogni riga cambiata buttava via anche le dipendenze.
COPY pom.xml .
RUN mvn -B --no-transfer-progress dependency:go-offline

COPY src ./src

# -DskipTests: i test girano nel CI, contro un MySQL vero (ce ne sono tre che
# parlano col database). Qui non c'e' nessun database, quindi eseguirli
# significherebbe farli fallire.
RUN mvn -B --no-transfer-progress clean package -DskipTests


# --- Fase di esecuzione -------------------------------------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app

# ⚠️ Il fuso orario, che per un gestionale di ore lavorate NON e' un dettaglio
# estetico.
#
# Questa immagine, senza TZ, parte in UTC. Il container di MySQL ha
# TZ=Europe/Rome. Da quel momento nel sistema ci sono due orologi a un'ora di
# distanza (due in estate), e si scrivono a vicenda nelle stesse tabelle:
# `creato_il` e `aggiornato_il` hanno DEFAULT CURRENT_TIMESTAMP e li scrive
# MySQL, mentre `avviata_il`, `chiusa_il` e `stato_aggiornato_il` li scrive la
# JVM. Le 24 chiamate a LocalDate.now()/LocalDateTime.now() del backend
# seguono questo fuso: con UTC, fra mezzanotte e l'una italiana «oggi» e'
# ieri, e una sessione avviata alle 00:30 finisce nel giorno sbagliato.
#
# Si dichiara qui, nell'immagine, e non solo nel compose: cosi' vale comunque
# la si avvii. Chi installa fuori dall'Italia lo sovrascrive con -e TZ=...
ENV TZ=Europe/Rome

# curl, per il HEALTHCHECK qui sotto. Va installato: l'immagine
# eclipse-temurin:25-jre non ha ne' curl ne' wget — verificato, non supposto.
# Un healthcheck scritto dando per scontato che ci siano fallisce sempre, e un
# container che si dichiara unhealthy per questo e' peggio di uno senza
# controlli, perche' il motore smette di crederci.
#
# E l'utente senza privilegi, nello stesso RUN per non aggiungere un livello.
# Prima non c'era nessun USER, quindi l'immagine girava come root e il
# securityContext di k8s doveva correggerla da fuori con runAsUser: cosi'
# l'immagine e' a posto da se' anche fuori da Kubernetes, dove quel
# securityContext non c'e'.
RUN apt-get update \
 && apt-get install --yes --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 10001 gestionale \
 && useradd --system --uid 10001 --gid gestionale gestionale

COPY --from=build --chown=10001:10001 /app/target/*.jar app.jar

USER 10001:10001

EXPOSE 8080

# Il controllo di salute: /actuator/health e' l'unica rotta che risponde senza
# autenticazione oltre al login, ed e' li' apposta. Il gruppo `readiness`
# comprende il controllo sul datasource, quindi questo healthcheck si accorge
# di un backend vivo che ha perso il database — che e' esattamente cio' che
# una prova sulla porta TCP non vedeva.
#
# start-period largo: al primo avvio Flyway applica tutte le migrazioni.
HEALTHCHECK --interval=15s --timeout=5s --start-period=180s --retries=5 \
    CMD ["sh", "-c", "curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1"]

ENTRYPOINT ["java", "-jar", "app.jar"]
