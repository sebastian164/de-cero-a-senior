#!/usr/bin/env bash
# Paso 3 del taller 08: rompe la regla de dependencias tres veces, una por regla, y exige que la prueba avise cada vez.
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"
TRABAJO="$1"                                  # carpeta temporal
rm -rf "$TRABAJO"; mkdir -p "$TRABAJO"

romper() {    # $1 nombre, $2 archivo relativo a src, $3 import a añadir, $4 texto que debe aparecer en el aviso
  local copia="$TRABAJO/$1"
  cp -R "$AQUI" "$copia"
  # el import va justo después de la línea package
  awk -v imp="$3" 'NR==1{print; print ""; print imp; next} {print}' "$copia/src/$2" > "$copia/src/$2.tmp" && mv "$copia/src/$2.tmp" "$copia/src/$2"
  javac -Xlint:all -Werror -d "$copia/salida" $(find "$copia/src" "$copia/pruebas" -name '*.java')
  echo "== Violación: $1 ($3 en $2)"
  set +e
  salida=$(cd "$copia" && java -cp salida tienda.SoloArquitectura)
  codigo=$?
  set -e
  echo "$salida" | grep FALLA
  if [ $codigo -eq 0 ] || ! echo "$salida" | grep -q "$4"; then
    echo "LA PRUEBA DE ARQUITECTURA NO AVISÓ"; exit 1
  fi
}

romper "dominio-importa-aplicacion" "tienda/dominio/Pedido.java" "import tienda.aplicacion.VistaDePedido;" "Pedido.java -> tienda.aplicacion.VistaDePedido"
romper "aplicacion-importa-adaptador" "tienda/aplicacion/ServicioDePedidos.java" "import tienda.adaptadores.salida.memoria.AlmacenEnMemoria;" "ServicioDePedidos.java -> tienda.adaptadores.salida.memoria.AlmacenEnMemoria"
romper "entrada-importa-salida" "tienda/adaptadores/entrada/consola/Consola.java" "import tienda.adaptadores.salida.memoria.AlmacenEnMemoria;" "Consola.java -> tienda.adaptadores.salida.memoria.AlmacenEnMemoria"
romper "entrada-usa-puerto-de-salida" "tienda/adaptadores/entrada/http/ServidorHttp.java" "import tienda.aplicacion.puertos.salida.Inventario;" "ServidorHttp.java -> tienda.aplicacion.puertos.salida.Inventario"
echo "La prueba de arquitectura avisó las cuatro veces."
