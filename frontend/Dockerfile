# --- Fase di build -----------------------------------------------------------
#
# node:22 e non node:20: Vite 8 pretende Node 20.19+ o 22+, quindi con la riga
# `node:20-alpine` il build funzionava solo finche' il tag `20` puntava a una
# 20.19 o successiva — cioe' per fortuna, non per dichiarazione.
FROM node:22-alpine AS build
WORKDIR /app

# package.json E package-lock.json, copiati prima del resto: finche' questi due
# non cambiano, Docker riusa il livello con le dipendenze gia' installate e il
# build salta l'intera installazione.
COPY package.json package-lock.json ./

# `npm ci` e non `npm install`, ed e' la differenza fra una build riproducibile
# e una che non lo e': `install` puo' aggiornare le dipendenze entro i vincoli
# del package.json e riscrivere il lockfile, quindi la stessa riga di codice
# costruita due volte puo' produrre due bundle diversi. `ci` installa
# esattamente le versioni del lockfile e fallisce se i due file non
# concordano, il che e' il momento giusto per accorgersene.
RUN npm ci

COPY . .

# Compila anche i tipi: lo script `build` e' `tsc -b && vite build`, quindi un
# errore di tipo ferma l'immagine qui invece di arrivare in produzione.
RUN npm run build


# --- Fase di distribuzione ----------------------------------------------------
FROM nginx:alpine

# La configurazione sta nell'immagine e non solo nella ConfigMap di k8s: senza,
# l'immagine da sola non inoltra /api e da 404 a ogni F5 fuori dalla radice.
# Il nome default.conf e' quello del file predefinito, quindi lo sostituisce;
# chi monta una ConfigMap su questo stesso percorso continua a vincere.
COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

# Il controllo di salute dell'immagine. wget c'e' gia' in nginx:alpine
# (busybox), quindi non serve installare niente.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1/ || exit 1

# Nessun USER: l'immagine nginx:alpine parte come root per scrivere il suo pid
# e i log, e poi scende a `nginx` per servire le richieste. Forzandola come
# utente qualunque il container muore in avvio. Per farla girare senza root
# serve `nginxinc/nginx-unprivileged`, che ascolta sulla 8080: e' un cambio di
# immagine e di porta, non una riga in piu' qui.
CMD ["nginx", "-g", "daemon off;"]
