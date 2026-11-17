# Imagen con salida independiente (output: 'standalone'). Se construye desde `next build` con SALIDA_INDEPENDIENTE=1 y DIST_DIR (scripts/construir-imagen.sh).
ARG DIST=.next-imagen
FROM node:26-slim
ARG DIST
WORKDIR /app
COPY ${DIST}/standalone ./
COPY ${DIST}/static ./${DIST}/static
COPY public ./public
ENV NODE_ENV=production PORT=3000 HOSTNAME=0.0.0.0 NEXT_TELEMETRY_DISABLED=1
EXPOSE 3000
CMD ["node", "server.js"]
