#!/usr/bin/env bash
# Deja en target/libs todo lo que necesita la imagen: el jar del servicio (con los talleres 04 a 06 dentro) y sus dependencias.
# Se ejecuta antes de `mvn test` (y antes de `docker compose up --build`), desde cualquier carpeta.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf target/libs
mvn -q -B package dependency:copy-dependencies -DskipTests -DoutputDirectory=target/libs -DincludeScope=runtime
cp target/servicio.jar target/libs/
ls target/libs | wc -l
