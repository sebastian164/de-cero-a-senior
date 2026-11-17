#!/usr/bin/env bash
# Tráfico continuo contra nginx (puerto $1) hasta que se borre el archivo $2. Unas 8 peticiones por segundo, repartidas en cuatro tipos.
puerto=$1; marca=$2; clave=$3
larga=$(printf 'y%.0s' $(seq 1 450))
i=0
while [ -f "$marca" ]; do
  i=$((i + 1))
  curl -s -m 10 -o /dev/null -X POST -H "X-Clave: $clave" -d "nota de trafico $i" "localhost:$puerto/api/notas" &
  curl -s -m 10 -o /dev/null "localhost:$puerto/api/notas" &
  curl -s -m 10 -o /dev/null "localhost:$puerto/api/info" &
  [ $((i % 4)) = 0 ] && curl -s -m 10 -o /dev/null -X POST -H "X-Clave: $clave" -d "$larga" "localhost:$puerto/api/notas" &
  sleep 0.4
  wait
done
