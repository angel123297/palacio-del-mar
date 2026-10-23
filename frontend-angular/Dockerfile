# Stage 1: Build Angular application
FROM node:22-alpine AS build
WORKDIR /app

# Configurar reintentos y timeouts de npm para evitar fallos por microcortes o inestabilidad de red (ECONNRESET)
RUN npm config set fetch-retries 5 \
    && npm config set fetch-retry-factor 2 \
    && npm config set fetch-retry-mintimeout 20000 \
    && npm config set fetch-retry-maxtimeout 120000 \
    && npm config set fetch-timeout 300000

COPY package*.json ./
RUN npm install --legacy-peer-deps --no-audit --no-fund
COPY . .
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine
COPY --from=build /app/dist/palacio-del-mar-web/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
