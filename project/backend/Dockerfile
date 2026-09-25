# ============================================
# Palacio del Mar - Backend (Express + MongoDB)
# ============================================
FROM node:20-alpine

ENV NODE_ENV=production
WORKDIR /app

# Primero solo package.json: así la capa de dependencias se reutiliza
# mientras no cambien las dependencias (builds mucho más rápidos).
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund \
    && npm cache clean --force

COPY . .

# No ejecutar como root dentro del contenedor
USER node

EXPOSE 5000
CMD ["node", "server.js"]
