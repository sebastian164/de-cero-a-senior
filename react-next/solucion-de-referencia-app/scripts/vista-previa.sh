#!/usr/bin/env bash
# Entorno de vista previa por pull request: una imagen y un contenedor por PR, con una URL propia.
# VISTA_HOST: nombre con el que otras personas alcanzan este servidor (por defecto 127.0.0.1, solo útil en local).
# Uso: vista-previa.sh levantar <n> | bajar <n>   (el puerto es 49560 + n; en un proveedor real sería una URL temporal)
set -euo pipefail
cd "$(dirname "$0")/.."
ACCION=${1:?levantar|bajar}; PR=${2:?número de PR}
PUERTO=$((49560 + PR)); NOMBRE=ref-react-next-pr-$PR; IMG=ref-react-next-tienda:pr-$PR
case $ACCION in
  levantar)
    docker rm -f "$NOMBRE" >/dev/null 2>&1 || true
    scripts/construir-imagen.sh "$IMG"
    docker run -d --name "$NOMBRE" -e INSTANCIA="pr-$PR" -p "$PUERTO:3000" "$IMG" >/dev/null
    for i in $(seq 1 60); do curl -sf -o /dev/null "http://127.0.0.1:$PUERTO/n5/04-renderizado/inicio" && break; sleep 1; done
    echo "http://${VISTA_HOST:-127.0.0.1}:$PUERTO" ;;
  bajar)
    docker rm -f "$NOMBRE" >/dev/null 2>&1 || true
    docker rmi -f "$IMG" >/dev/null 2>&1 || true
    echo "vista previa del PR $PR eliminada" ;;
esac
