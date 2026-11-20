#!/usr/bin/env bash
# Solución ejecutable del taller Docker 06. Registro local (registry:3) en 127.0.0.1:50900 en lugar de ghcr.io.
# Compose: dev en 50901, prod en 50902. Otros puertos: 50903 y 50905.
# Trabaja sobre una copia de repositorio/ con el código de docker-kubernetes/app; no modifica esta carpeta.
# Herramientas: Docker con Buildx y emulación (QEMU) para arm64/amd64, Trivy y actionlint.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
AQUI="$(cd "$(dirname "$0")" && pwd)"
P=$PREFIJO-d06
REG=localhost:50900
OUT="$SALIDAS/docker-06.txt"; : > "$OUT"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/$PREFIJO-d06.XXXXXX")"
BUILDER=$PREFIJO-d06-builder
SOL="$TMP/repo"
for h in trivy actionlint; do command -v $h >/dev/null || { echo "Falta $h (ver README.md)"; exit 2; }; done
docker buildx version >/dev/null 2>&1 || { echo "Falta docker buildx"; exit 2; }
registrar() { echo "$1" >> "$OUT"; nota "$1"; }
limpiar() {
  docker compose -p $P-dev down -v >/dev/null 2>&1; docker compose -p $P-prod down -v >/dev/null 2>&1
  docker rm -fv $P-registro $P-front $P-otra-maquina $P-amd64 >/dev/null 2>&1
  docker buildx rm $BUILDER >/dev/null 2>&1 || true
}
limpiar

titulo "Paso 1: repositorio propio con app/, Dockerfile y compose.yaml"
montar_solucion "$TMP/repo" app "$AQUI/repositorio"
cd "$TMP/repo"
# El primer commit lleva la base con la que se construye la 1.0.0 (nginx 1.28); el arreglo de la 1.0.1 es un cambio real en el historial.
sed_i 's|^ARG BASE=nginx:1.30-alpine|ARG BASE=nginx:1.28-alpine|' app/web/Dockerfile
git init -q . && git add -A && git -c user.name=alumno -c user.email=v@e.local commit -q -m "bitacora 1.0.0"
COMMIT1="$(git rev-parse --short HEAD)"
ok "repositorio con commit $COMMIT1"

titulo "Paso 2 y 3: registro, builder multiarquitectura y publicación"
docker run -d --name $P-registro -p 127.0.0.1:50900:5000 registry:3 >/dev/null
esperar_hasta 30 curl -sf http://$REG/v2/
docker buildx create --name $BUILDER --driver docker-container --driver-opt network=host >/dev/null
docker buildx inspect $BUILDER --bootstrap >/dev/null
construir() { # construir imagen version commit [args extra]
  local img=$1 ver=$2 com=$3; shift 3
  docker buildx build --builder $BUILDER --platform linux/amd64,linux/arm64 \
    --label org.opencontainers.image.revision="$com" \
    --label org.opencontainers.image.source="https://github.com/ejemplo/bitacora" \
    --label org.opencontainers.image.version="$ver" \
    -t $REG/bitacora-$img:$ver -t $REG/bitacora-$img:$com "$@" --push app/$img >> "$TMP/build-$img-$ver.log" 2>&1
}
i=$(ahora_ms)
construir api 1.0.0 $COMMIT1 && ok "api 1.0.0 construida y publicada para dos arquitecturas ($(( ($(ahora_ms)-i)/1000 )) s)" || { mal "construir api"; tail -15 "$TMP/build-api-1.0.0.log"; }
# La web 1.0.0 se construye tal cual está en su commit (sin --build-arg): el Dockerfile de ese commit lleva nginx:1.28-alpine.
i=$(ahora_ms)
construir web 1.0.0 $COMMIT1 && ok "web 1.0.0 construida y publicada ($(( ($(ahora_ms)-i)/1000 )) s)" || { mal "construir web"; tail -15 "$TMP/build-web-1.0.0.log"; }
for img in api web; do
  docker buildx imagetools inspect $REG/bitacora-$img:1.0.0 | sed -n '/Manifests:/,$p' | grep -E 'Platform' | sed "s/^/     $img: /"
  igual "$img: el nombre contiene amd64 y arm64" "linux/amd64 linux/arm64" "$(docker buildx imagetools inspect $REG/bitacora-$img:1.0.0 | awk '/Platform:/ && $2 !~ /unknown/ {print $2}' | sort | tr '\n' ' ' | sed 's/ $//')"
  d1="$(docker buildx imagetools inspect $REG/bitacora-$img:1.0.0 | awk '/^Digest:/{print $2}')"
  d2="$(docker buildx imagetools inspect $REG/bitacora-$img:$COMMIT1 | awk '/^Digest:/{print $2}')"
  igual "$img: la etiqueta de versión y la del commit son la misma imagen" "$d1" "$d2"
done

titulo "Criterios: descargar y ejecutar solo con nombre y versión; sin latest; imagen -> commit"
docker rmi -f $REG/bitacora-api:1.0.0 >/dev/null 2>&1
docker run -d --name $P-otra-maquina -e MENSAJE=desde-otra-maquina -p 127.0.0.1:50903:8080 $REG/bitacora-api:1.0.0 >/dev/null
esperar_hasta 30 curl -sf http://127.0.0.1:50903/salud/listo
igual "docker run con solo nombre:versión descarga y arranca" desde-otra-maquina "$(curl -s http://127.0.0.1:50903/api/info | python3 -c 'import sys,json; print(json.load(sys.stdin)["mensaje"])')"
docker rm -fv $P-otra-maquina >/dev/null
afirmar "ningún Dockerfile usa latest ni una base sin etiqueta" python3 - "$SOL/app" <<'PY'
import re,sys,glob
bad=0
for f in glob.glob(sys.argv[1]+"/*/Dockerfile"):
    t=open(f).read(); args=dict(re.findall(r"^ARG (\w+)=(\S+)",t,re.M))
    for img in re.findall(r"^FROM (\S+)",t,re.M):
        img=re.sub(r"\$\{(\w+)\}",lambda m:args.get(m.group(1),""),img)
        if ":" not in img or img.endswith(":latest"): print("MAL",f,img); bad+=1
sys.exit(1 if bad else 0)
PY
registrar "etiquetas_publicadas: $(curl -s http://$REG/v2/bitacora-api/tags/list) $(curl -s http://$REG/v2/bitacora-web/tags/list)"
afirmar "no existe la etiqueta latest en el registro" sh -c "! curl -s http://$REG/v2/bitacora-api/tags/list | grep -q latest && ! curl -s http://$REG/v2/bitacora-web/tags/list | grep -q latest"
rev="$(docker buildx imagetools inspect $REG/bitacora-api:1.0.0 --format '{{json .Image}}' | python3 -c '
import sys,json
d=json.load(sys.stdin)
for p,v in d.items():
    print(v["config"]["Labels"]["org.opencontainers.image.revision"]); break')"
igual "de la imagen al commit (etiqueta revision)" "$COMMIT1" "$rev"
igual "y el commit existe en el repositorio" commit "$(git cat-file -t $rev)"

titulo "Paso 4: análisis de vulnerabilidades (Trivy $(trivy --version | head -1 | awk '{print $2}'))"
analizar() { # analizar imagen -> JSON en $TMP/<nombre>.json y resumen
  trivy image --quiet --insecure --image-src remote --platform linux/amd64 --severity HIGH,CRITICAL --format json "$1" > "$TMP/$2.json" 2>"$TMP/$2.err"
  python3 - "$TMP/$2.json" <<'PY'
import sys,json,collections
d=json.load(open(sys.argv[1])); c=collections.Counter(); pk=collections.Counter()
for r in d.get("Results",[]):
    for v in r.get("Vulnerabilities") or []:
        k=("con arreglo" if v.get("FixedVersion") else "sin arreglo", v["Severity"]); c[k]+=1; pk[v["PkgName"]]+=1
print(" ".join(f"{a}/{b}={n}" for (a,b),n in sorted(c.items())) or "sin hallazgos HIGH/CRITICAL", "| paquetes:", ", ".join(f"{p}({n})" for p,n in pk.most_common(6)))
PY
}
res_api="$(analizar $REG/bitacora-api:1.0.0 api100)"; registrar "trivy api 1.0.0: $res_api"
res_web="$(analizar $REG/bitacora-web:1.0.0 web100)"; registrar "trivy web 1.0.0: $res_web"
afirmar "la web 1.0.0 (nginx 1.28) tiene hallazgos altos o críticos" sh -c "echo '$res_web' | grep -q 'con arreglo'"
nota "clasificación de la web: los de 'con arreglo' se resuelven actualizando la base; los de 'sin arreglo' no tienen versión corregida todavía; los que no afectan (p. ej. libxslt, tiff: nginx no los carga en este uso) se documentan en ANALISIS.md"

titulo "Paso 5: corregir y publicar una versión NUEVA (1.0.1), sin reutilizar 1.0.0"
sed_i 's|^ARG BASE=nginx:1.28-alpine|ARG BASE=nginx:1.30-alpine|' app/web/Dockerfile
git add -A; git -c user.name=alumno -c user.email=v@e.local commit -q -m "web 1.0.1: la base pasa de nginx 1.28 a 1.30"
COMMIT2="$(git rev-parse --short HEAD)"
construir web 1.0.1 $COMMIT2 --pull && ok "web 1.0.1 publicada (commit $COMMIT2)" || { mal "construir web 1.0.1"; tail -15 "$TMP/build-web-1.0.1.log"; }
construir api 1.0.1 $COMMIT2 --pull && ok "api 1.0.1 publicada para igualar versiones" || mal "construir api 1.0.1"
res_web2="$(analizar $REG/bitacora-web:1.0.1 web101)"; registrar "trivy web 1.0.1: $res_web2"
res_api2="$(analizar $REG/bitacora-api:1.0.1 api101)"; registrar "trivy api 1.0.1: $res_api2"
n_antes="$(python3 -c 'import json;d=json.load(open("'$TMP'/web100.json"));print(sum(len(r.get("Vulnerabilities") or []) for r in d["Results"]))')"
n_desp="$(python3 -c 'import json;d=json.load(open("'$TMP'/web101.json"));print(sum(len(r.get("Vulnerabilities") or []) for r in d["Results"]))')"
registrar "web: HIGH+CRITICAL 1.0.0=$n_antes 1.0.1=$n_desp"
afirmar "la 1.0.1 tiene menos hallazgos que la 1.0.0 ($n_desp < $n_antes)" test "$n_desp" -lt "$n_antes"
igual "las dos versiones siguen publicadas (1.0.0 no se tocó)" "1.0.0 1.0.1" "$(curl -s http://$REG/v2/bitacora-web/tags/list | python3 -c 'import sys,json; print(" ".join(t for t in sorted(json.load(sys.stdin)["tags"]) if t.count(".")==2))')"

titulo "Trazabilidad: el commit de cada imagen contiene la base con la que se construyó"
afirmar "git show $COMMIT1:app/web/Dockerfile tiene nginx:1.28-alpine" sh -c "git show $COMMIT1:app/web/Dockerfile | grep -q '^ARG BASE=nginx:1.28-alpine'"
afirmar "git show $COMMIT2:app/web/Dockerfile tiene nginx:1.30-alpine" sh -c "git show $COMMIT2:app/web/Dockerfile | grep -q '^ARG BASE=nginx:1.30-alpine'"
rev_web1="$(docker buildx imagetools inspect $REG/bitacora-web:1.0.0 --format '{{json .Image}}' | python3 -c 'import sys,json; d=json.load(sys.stdin); print(next(iter(d.values()))["config"]["Labels"]["org.opencontainers.image.revision"])')"
rev_web2="$(docker buildx imagetools inspect $REG/bitacora-web:1.0.1 --format '{{json .Image}}' | python3 -c 'import sys,json; d=json.load(sys.stdin); print(next(iter(d.values()))["config"]["Labels"]["org.opencontainers.image.revision"])')"
igual "web 1.0.0 apunta a su commit" "$COMMIT1" "$rev_web1"
igual "web 1.0.1 apunta a su commit" "$COMMIT2" "$rev_web2"
v1="$(docker run --rm --platform linux/arm64 --entrypoint nginx $REG/bitacora-web:1.0.0 -v 2>&1 | grep -o 'nginx/[0-9.]*')"; v2="$(docker run --rm --platform linux/arm64 --entrypoint nginx $REG/bitacora-web:1.0.1 -v 2>&1 | grep -o 'nginx/[0-9.]*')"
registrar "nginx dentro de web 1.0.0: $v1 | web 1.0.1: $v2"
afirmar "la imagen 1.0.0 es nginx 1.28 y la 1.0.1 es nginx 1.30, como dicen sus commits" sh -c "echo '$v1' | grep -q 'nginx/1.28' && echo '$v2' | grep -q 'nginx/1.30'"
registrar "historial: $(git log --format='%h %s' | tr '\n' '|')"

titulo "La variante amd64 se ejecuta (emulada: el equipo es $(uname -m))"
docker rm -fv $P-amd64 >/dev/null 2>&1
docker pull -q --platform linux/amd64 $REG/bitacora-api:1.0.1 >/dev/null
docker run -d --name $P-amd64 --platform linux/amd64 -p 127.0.0.1:50905:8080 $REG/bitacora-api:1.0.1 >/dev/null
esperar_hasta 120 curl -sf http://127.0.0.1:50905/salud/listo
igual "amd64: /salud/listo responde 200" 200 "$(codigo_http http://127.0.0.1:50905/salud/listo)"
dig_amd64="$(docker buildx imagetools inspect $REG/bitacora-api:1.0.1 | awk '/^ *Name:/ {n=$2} /Platform:/ && $2=="linux/amd64" {print n}' | sed 's/.*@//')"
igual "amd64: el contenedor corre el manifiesto linux/amd64 del índice" "$dig_amd64" "$(docker inspect -f '{{.ImageManifestDescriptor.digest}}' $P-amd64)"
igual "amd64: dentro del contenedor uname -m" x86_64 "$(docker exec $P-amd64 uname -m)"
registrar "amd64: $(curl -s http://127.0.0.1:50905/api/info | cut -c1-100)"
docker rm -fv $P-amd64 >/dev/null
# Se vuelve a la variante nativa para que Compose (paso 7) use la arm64 y no la emulada.
docker rmi -f $REG/bitacora-api:1.0.1 >/dev/null; docker pull -q --platform linux/arm64 $REG/bitacora-api:1.0.1 >/dev/null

titulo "Paso 6: lista de componentes"
mkdir -p sbom
for img in api web; do
  trivy image --quiet --insecure --image-src remote --platform linux/amd64 --format cyclonedx -o "sbom/bitacora-$img-1.0.1.cdx.json" $REG/bitacora-$img:1.0.1
  n="$(python3 -c 'import json;print(len(json.load(open("sbom/bitacora-'$img'-1.0.1.cdx.json"))["components"]))')"
  registrar "sbom $img 1.0.1: $n componentes"
  afirmar "SBOM de $img con componentes" test "$n" -gt 5
done
git add -A; git -c user.name=alumno -c user.email=v@e.local commit -q -m "sbom"

titulo "Paso 7: misma imagen, dos ambientes con Compose, sin reconstruir"
export REGISTRO=$REG VERSION_IMAGEN=1.0.1 VERSION_IMAGEN_WEB=1.0.1
MENSAJE="Hola, desarrollo" VERSION=dev PUERTO_WEB=50901 docker compose -p $P-dev up -d --wait >/dev/null 2>&1 && ok "dev levantado" || mal "dev"
MENSAJE="Hola, produccion" VERSION=1.0.1 PUERTO_WEB=50902 docker compose -p $P-prod up -d --wait >/dev/null 2>&1 && ok "prod levantado" || mal "prod"
igual "dev responde su mensaje" "Hola, desarrollo" "$(curl -s localhost:50901/api/info | python3 -c 'import sys,json; print(json.load(sys.stdin)["mensaje"])')"
igual "prod responde su mensaje" "Hola, produccion" "$(curl -s localhost:50902/api/info | python3 -c 'import sys,json; print(json.load(sys.stdin)["mensaje"])')"
ia="$(docker compose -p $P-dev images api -q)"; ib="$(docker compose -p $P-prod images api -q)"
igual "es literalmente la misma imagen (mismo ID)" "$ia" "$ib"
registrar "compose: dev=$(curl -s localhost:50901/api/info | tr -d '\n' | cut -c1-120) prod=$(curl -s localhost:50902/api/info | tr -d '\n' | cut -c1-120)"
docker compose -p $P-dev down -v >/dev/null 2>&1; docker compose -p $P-prod down -v >/dev/null 2>&1

titulo "Paso 8: el flujo de integración continua"
afirmar "actionlint valida el flujo" actionlint "$SOL/.github/workflows/publicar.yml"
afirmar "se dispara al crear una etiqueta de versión" grep -q 'tags:' "$SOL/.github/workflows/publicar.yml"
afirmar "sin secretos escritos: usa GITHUB_TOKEN" sh -c "grep -q 'secrets.GITHUB_TOKEN' '$SOL/.github/workflows/publicar.yml' && ! grep -Ei 'password: *[a-z0-9]{8}|ghp_' '$SOL/.github/workflows/publicar.yml'"
afirmar "el análisis va antes de publicar" sh -c "[ \$(grep -n 'Analizar' '$SOL/.github/workflows/publicar.yml' | head -1 | cut -d: -f1) -lt \$(grep -n 'name: Publicar' '$SOL/.github/workflows/publicar.yml' | cut -d: -f1) ]"
# La condición de la puerta, la misma que usa el flujo, ejecutada con Trivy contra imágenes reales:
trivy image --quiet --severity CRITICAL --ignore-unfixed --exit-code 1 $REG/bitacora-web:1.0.0 --insecure --image-src remote --platform linux/amd64 >/dev/null 2>&1; g100=$?
trivy image --quiet --severity CRITICAL --ignore-unfixed --exit-code 1 $REG/bitacora-web:1.0.1 --insecure --image-src remote --platform linux/amd64 >/dev/null 2>&1; g101=$?
registrar "puerta (CRITICAL con arreglo): web 1.0.0 exit=$g100, web 1.0.1 exit=$g101"
igual "la puerta detiene la web 1.0.0 (críticas con arreglo): exit" 1 "$g100"
igual "la 1.0.1 pasa la puerta" 0 "$g101"
act --version >/dev/null 2>&1 && nota "act $(act --version | awk '{print $3}') instalado: el flujo no se ejecuta aquí porque publica en ghcr.io con el token de GitHub"

titulo "Paso 9 (opcional)"
nota "El frontend propio en varias etapas depende de tu propio proyecto: esta prueba no lo incluye."

titulo "Paso 10: el README del repositorio"
afirmar "README con el arranque en cinco minutos y sin credenciales" sh -c "grep -q 'cinco minutos' '$SOL/README.md' && ! grep -Ei 'password|token=' '$SOL/README.md'"

titulo "Limpieza"
cd /; limpiar; docker rmi $REG/bitacora-api:1.0.0 >/dev/null 2>&1; rm -rf "$TMP"; ok "registro, builder y proyectos borrados"
terminar "Docker 06"
