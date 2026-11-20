#!/bin/sh
# Entrada de la API. Si hay un secreto de Compose (archivo en /run/secrets), lo exporta solo para el proceso de Java.
# Las opciones de la JVM viven solo aquí (y no también en compose.yaml).
if [ -f /run/secrets/clave_api ]; then CLAVE_API="$(cat /run/secrets/clave_api)"; export CLAVE_API; fi
exec java -XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -cp /app Servidor
