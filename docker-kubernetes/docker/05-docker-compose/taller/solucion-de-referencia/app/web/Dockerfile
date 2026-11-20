# Bitácora web: página estática y plantilla de nginx.
# La imagen oficial procesa /etc/nginx/templates/*.template con envsubst al arrancar
# y deja el resultado en /etc/nginx/conf.d/ (default.conf.template -> default.conf).
FROM nginx:1.28-alpine
COPY index.html /usr/share/nginx/html/
COPY default.conf.template /etc/nginx/templates/
EXPOSE 8080
