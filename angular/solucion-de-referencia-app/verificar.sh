#!/usr/bin/env bash
# Verificación de la ruta Angular, de principio a fin. Código 0 solo si TODO pasa.
# Uso: ./verificar.sh            (usa los puertos 49200-49299 y no deja procesos vivos)
set -uo pipefail
cd "$(dirname "$0")"
RAIZ="$PWD"
export CI=true NX_DAEMON=false NX_NO_CLOUD=true FORCE_COLOR=0
LOG="$RAIZ/tmp/verificar.log"; mkdir -p "$RAIZ/tmp" "$RAIZ/capturas"; : > "$LOG"
FALLOS=()
paso() { # paso "nombre" comando...
  local nombre="$1"; shift
  printf '>> %s\n' "$nombre"
  if ( "$@" ) >>"$LOG" 2>&1; then printf '   OK\n'; else printf '   FALLO (ver tmp/verificar.log)\n'; FALLOS+=("$nombre"); fi
}
pruebas_unitarias() { # reintenta UNA vez solo si Vitest murió por una señal (Worker exited unexpectedly) y ninguna prueba falló
  local salida="$RAIZ/tmp/ng-test.log"
  npx ng test app >"$salida" 2>&1; local rc=$?
  cat "$salida"
  if [ $rc -ne 0 ] && grep -q 'Worker exited unexpectedly' "$salida" && ! grep -Eq '[0-9]+ failed|FAIL ' "$salida"; then
    echo "REINTENTO ÚNICO: Vitest informó 'Worker exited unexpectedly' sin pruebas fallidas (carga alta del equipo); se vuelve a ejecutar"
    npx ng test app; rc=$?
  fi
  return $rc
}
e2e() { # reintenta UNA vez, solo las pruebas fallidas, y solo si alguna agotó su tiempo (carga alta del equipo)
  local salida="$RAIZ/tmp/e2e.log"
  npx playwright test >"$salida" 2>&1; local rc=$?
  cat "$salida"
  if [ $rc -ne 0 ] && grep -q 'Test timeout of' "$salida"; then
    echo "REINTENTO ÚNICO: una prueba agotó su tiempo (carga alta del equipo); se repiten solo las fallidas"
    npx playwright test --last-failed; rc=$?
  fi
  return $rc
}
limpiar() { # no deja servidores vivos en nuestro bloque
  for p in $(lsof -ti tcp:49200-49299 -sTCP:LISTEN 2>/dev/null); do kill "$p" 2>/dev/null; done
  for p in $(pgrep -f 'ver-angular' 2>/dev/null); do kill "$p" 2>/dev/null; done
  rm -rf "$RAIZ/.ci-limpio"
}
trap limpiar EXIT
limpiar; sleep 2

# 0. Dependencias (desde cero si faltan)
for d in . proyectos/nx-tienda proyectos/mfe proyectos/openapi proyectos/lighthouse; do
  if [ ! -d "$d/node_modules" ]; then paso "npm ci en $d" bash -c "cd $d && npm ci"; fi
done
export CHROME_PATH="$(node -e "console.log(require('@playwright/test').chromium.executablePath())")"
[ -x "$CHROME_PATH" ] || paso "instalar Chromium de Playwright" npx playwright install chromium

# 1. Construcciones
paso "ng build app (producción)" npx ng build app
paso "ng build n6-01-zona" npx ng build n6-01-zona
paso "ng build n6-01-sin-zona" npx ng build n6-01-sin-zona
paso "ng build n6-02-ssr (producción)" npx ng build n6-02-ssr --output-path tmp/n6-02-prod
paso "ng build n6-02-ssr (desarrollo)" npx ng build n6-02-ssr --configuration development --output-path tmp/n6-02-dev
paso "ng build n6-04-i18n (es-CO y en)" npx ng build n6-04-i18n
paso "ng build n5-01 (mediciones: se construyen dentro del script)" true

# 2. Calidad estática
paso "eslint (talleres, ejemplos, src, e2e)" npx eslint talleres proyectos/ejemplos src e2e
paso "casos que NO deben compilar (compilacion-negativa)" node proyectos/compilacion-negativa/comprobar.mjs

# 3. Pruebas unitarias y de componentes (Vitest, 28 ejemplos y 28 talleres)
paso "ng test app (todas las pruebas unitarias)" pruebas_unitarias

# 4. Pruebas E2E en el navegador (Playwright): pantallas, capturas y errores de consola
paso "playwright: todas las pantallas y E2E de los talleres" e2e

# 5. Scripts de medición y comprobación de los talleres
for s in \
  talleres/nivel-03-patrones/05-adapter-interceptores-y-guardas/medir-renombre.mjs \
  talleres/nivel-04-clean-code-y-testing/03-pruebas-unitarias-y-de-componentes/mutacion.mjs \
  talleres/nivel-04-clean-code-y-testing/04-pruebas-e2e-y-estrategia/medir-niveles.mjs \
  talleres/nivel-04-clean-code-y-testing/04-pruebas-e2e-y-estrategia/presupuesto.mjs \
  talleres/nivel-05-arquitectura/01-estructura-por-funcionalidades/grafo.mjs \
  talleres/nivel-05-arquitectura/01-estructura-por-funcionalidades/limites.mjs \
  talleres/nivel-05-arquitectura/01-estructura-por-funcionalidades/medir-paquete.mjs \
  talleres/nivel-05-arquitectura/03-arquitectura-limpia-en-frontend/limite-dominio.mjs \
  talleres/nivel-05-arquitectura/04-comunicacion-con-el-backend/openapi/generar-y-romper.mjs \
  talleres/nivel-06-angular-avanzado/01-deteccion-de-cambios-y-rendimiento/analizar-paquete.mjs; do
  paso "node $s" node "$s"
done
# El análisis del paquete reconstruye 'app': se vuelve a construir el de producción para lo que sigue
paso "ng build app (de nuevo)" npx ng build app
paso "buscar secretos en el paquete" node talleres/nivel-06-angular-avanzado/03-seguridad-en-el-frontend/buscar-secretos.mjs
paso "autoprueba del buscador de secretos" node talleres/nivel-06-angular-avanzado/03-seguridad-en-el-frontend/buscar-secretos.mjs --autoprueba
paso "npm audit (0 vulnerabilidades)" npm audit
paso "Lighthouse móvil (n6-01 punto 1)" node proyectos/lighthouse/medir.mjs
paso "esquemas de migración a señales (n1-03 punto 7)" talleres/nivel-01-fundamentos/03-componentes-con-senales/migracion/ejecutar-migracion.sh
paso "workflow de accesibilidad en copia limpia (n6-04 punto 7)" talleres/nivel-06-angular-avanzado/04-accesibilidad-e-internacionalizacion/ci/probar-workflow.sh

# 6. Monorepo Nx y microfrontend
paso "Nx: límites, grafo, afectados, sistema de diseño, lint+test+build" node talleres/nivel-05-arquitectura/05-monorepos-y-microfrontends/ejecutar-nx.mjs
# lint por separado de build: api:build (rolldown) crea y borra un rolldown.config.*.js temporal mientras ESLint recorre la carpeta
paso "Nx: nx run-many -t lint" bash -c "cd proyectos/nx-tienda && npx nx run-many -t lint --skip-nx-cache"
paso "Nx: nx run-many -t test build" bash -c "cd proyectos/nx-tienda && npx nx run-many -t test build --skip-nx-cache"
paso "microfrontend: build remoto" bash -c "cd proyectos/mfe && npx ng build remote"
paso "microfrontend: build anfitrión" bash -c "cd proyectos/mfe && npx ng build host"
paso "microfrontend: anfitrión + remoto en el navegador" node talleres/nivel-05-arquitectura/05-monorepos-y-microfrontends/microfrontend.mjs

# 7. Fin: nada vivo
limpiar; sleep 2
VIVOS="$(lsof -ti tcp:49200-49299 -sTCP:LISTEN 2>/dev/null | wc -l | tr -d ' ')"
if [ "$VIVOS" != "0" ]; then FALLOS+=("quedan $VIVOS procesos escuchando en 49200-49299"); fi
echo
if [ "${#FALLOS[@]}" -eq 0 ]; then echo "VERIFICACIÓN COMPLETA: TODO PASA"; exit 0; fi
echo "FALLARON ${#FALLOS[@]} pasos:"; printf ' - %s\n' "${FALLOS[@]}"; exit 1
