#!/usr/bin/env bash
# Funciones de apoyo de esta solución. Se carga con: source comun.sh
paso() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
ok() { printf '  OK  %s\n' "$*"; }
falla() { printf '  FALLA  %s\n' "$*" >&2; exit 1; }
comprobar() { # comprobar "descripción" comando...
  local d=$1; shift
  if "$@"; then ok "$d"; else falla "$d"; fi
}
# extraer_bloque README n lenguaje: imprime el n-ésimo bloque (desde 1) con ese lenguaje
extraer_bloque() {
  awk -v n="$2" -v l="$3" '
    $0 ~ "^```"l"$" {c++; if (c==n) {f=1; next}}
    f && /^```/ {exit}
    f {print}' "$1"
}
esperar_http() { # esperar_http url segundos
  local i
  for i in $(seq 1 "$2"); do curl -sf -o /dev/null "$1" && return 0; sleep 1; done
  return 1
}
requiere() { local c; for c in "$@"; do command -v "$c" >/dev/null || { echo "Falta '$c': instálalo (ver README.md)" >&2; exit 2; }; done; }
