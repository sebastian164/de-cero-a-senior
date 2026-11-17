#!/usr/bin/env bash
# Ejecuta los pasos `run:` de accesibilidad.yml, en el mismo orden, sobre una COPIA LIMPIA del proyecto
# (sin node_modules, dist, tmp ni capturas), como lo haría el runner de GitHub Actions tras actions/checkout.
# `uses:` (checkout, setup-node) no se ejecutan: la copia y el Node local los reemplazan.
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"; RAIZ="$(cd "$AQUI/../../../.." && pwd)"
COPIA="$RAIZ/.ci-limpio"  # fuera de cualquier carpeta llamada tmp: Playwright ignora **/tmp/**
rm -rf "$COPIA"; mkdir -p "$COPIA"
rsync -a --exclude node_modules --exclude dist --exclude tmp --exclude .ci-limpio --exclude capturas --exclude resultados-e2e --exclude .angular \
  --exclude proyectos/nx-tienda --exclude proyectos/mfe --exclude proyectos/openapi --exclude proyectos/lighthouse "$RAIZ/" "$COPIA/"
cd "$COPIA"
ruby -ryaml -e 'puts YAML.load_file(ARGV[0])["jobs"]["axe"]["steps"].map { |s| s["run"] }.compact' "$AQUI/accesibilidad.yml" > "$COPIA/pasos.txt"
echo "Pasos del workflow:"; cat "$COPIA/pasos.txt"
grep -q 'ng build n6-04-i18n' pasos.txt || { echo "FALLO: el workflow no construye n6-04-i18n"; exit 1; }
[ ! -d node_modules ] && [ ! -d dist ] && [ ! -d tmp ] || { echo "FALLO: la copia no está limpia"; exit 1; }
export CI=true PUERTO_E2E=49290
while IFS= read -r paso; do
  # `--with-deps` instala paquetes del sistema de Linux; en este equipo Chromium ya está instalado.
  paso="${paso//--with-deps /}"
  echo ">> $paso"
  if [[ "$paso" == *"playwright test"* ]]; then
    bash -c "$paso" 2>&1 | tee salida-e2e.txt; [ "${PIPESTATUS[0]}" -eq 0 ] || exit 1
    grep -Eq '(^|[^0-9])6 passed' salida-e2e.txt || { echo "FALLO: se esperaban exactamente 6 pruebas pasadas (6 passed)"; exit 1; }
  else
    bash -c "$paso"
  fi
done < pasos.txt
echo "WORKFLOW OK"
rm -rf "$COPIA"
