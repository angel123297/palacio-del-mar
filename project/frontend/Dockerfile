# ============================================
# Palacio del Mar - Frontend (React + Vite -> nginx)
# El contexto de build es esta carpeta (./frontend), ver docker-compose.yml.
#
# Antes esta imagen solo copiaba un bundle ya compilado y usaba "sed" para
# reescribir a mano la URL de la API dentro del JS minificado. Ahora existe
# código fuente real: el frontend llama siempre a la ruta relativa "/api"
# (ver src/api/client.js), así que no hace falta ningún truco de sed ni
# variables de entorno en tiempo de build.
# ============================================

# --- Etapa 1: compilar con Vite ---
FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

# --- Etapa 2: servir con nginx ---
FROM nginx:1.27-alpine

RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
