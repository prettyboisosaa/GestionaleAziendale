# Fase di build
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# Fase di distribuzione con Nginx
FROM nginx:alpine
# Se usi Vite la cartella di output è 'dist' (se usi Create React App cambia in 'build')
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]