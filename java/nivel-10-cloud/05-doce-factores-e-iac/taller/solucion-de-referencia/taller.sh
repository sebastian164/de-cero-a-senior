#!/usr/bin/env bash
# Solución del taller de la sesión 05 (doce factores e IaC), con OpenTofu contra LocalStack: estado remoto con bloqueo,
# dos ambientes con el mismo código, deriva, destruir y reconstruir, y sesiones en Redis y archivos en S3 con dos instancias.
# Levanta LocalStack, Redis y PostgreSQL con Docker Compose y lo baja todo al terminar.
# Variables opcionales: PROYECTO (n10-05), PUERTO_LOCALSTACK (4566), PUERTO_REDIS (6379), PUERTO_PG (5432),
# PUERTO_A (8084), PUERTO_B (8085), PUERTO_DOCE (8086), TRIVY_CACHE, TF_PLUGIN_CACHE_DIR.
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"; source "$AQUI/comun.sh"
requiere docker tofu aws jq curl java trivy rsync
cd "$AQUI"
export PROYECTO=${PROYECTO:-n10-05}
export PUERTO_LOCALSTACK=${PUERTO_LOCALSTACK:-4566} PUERTO_REDIS=${PUERTO_REDIS:-6379} PUERTO_PG=${PUERTO_PG:-5432}
export PUERTO_A=${PUERTO_A:-8084} PUERTO_B=${PUERTO_B:-8085} PUERTO_DOCE=${PUERTO_DOCE:-8086}
export TRIVY_CACHE=${TRIVY_CACHE:-$HOME/.cache/curso-trivy}; mkdir -p "$TRIVY_CACHE"
export TF_PLUGIN_CACHE_DIR=${TF_PLUGIN_CACHE_DIR:-$HOME/.cache/curso-tofu-plugins}; mkdir -p "$TF_PLUGIN_CACHE_DIR"
ENDPOINT=http://localhost:$PUERTO_LOCALSTACK
export TF_VAR_endpoint=$ENDPOINT
AWSL() { aws --endpoint-url "$ENDPOINT" "$@"; }
# El trabajo (estado local de iac-readme, JAR compilado, registros) va a una copia temporal: esta carpeta queda limpia.
TRABAJO=$(mktemp -d)
cp -R "$AQUI/infra" "$AQUI/iac-readme" "$TRABAJO/"
sed -i.bak "s#http://localhost:4566#$ENDPOINT#g" "$TRABAJO/infra/backend.hcl" "$TRABAJO/iac-readme/proveedor.tf" && rm -f "$TRABAJO"/infra/*.bak "$TRABAJO"/iac-readme/*.bak
RESULTADOS=$TRABAJO/resultados.txt; : > "$RESULTADOS"
anotar() { echo "$*" | tee -a "$RESULTADOS" | sed 's/^/    /'; }
PIDS=()
parar() { for p in ${PIDS[@]+"${PIDS[@]}"}; do [ -n "$p" ] && kill "$p" 2>/dev/null || true; done; PIDS=(); sleep 2; }
bajar() {
  parar
  docker compose -p "$PROYECTO" -f "$AQUI/compose.yaml" down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$TRABAJO"
}
trap bajar EXIT

paso "Compilando la aplicación y levantando LocalStack, Redis y PostgreSQL"
rsync -a --exclude target "$AQUI/aplicacion/" "$TRABAJO/app/"
( cd "$TRABAJO/app" && ./mvnw -q -B -DskipTests package ) || falla "no se pudo compilar la aplicación (necesitas JDK 21)"
JAR=$TRABAJO/app/target/pedidos-1.4.2.jar; test -s "$JAR"
docker compose -p "$PROYECTO" -f "$AQUI/compose.yaml" up -d --wait >/dev/null
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 AWS_REGION=us-east-1
export TF_VAR_clave_bd="clave-$RANDOM-$RANDOM"
borrar_bucket_versionado() { # vacía todas las versiones y marcas de borrado, y elimina el bucket
  local b=$1 lote
  AWSL s3api head-bucket --bucket "$b" 2>/dev/null || return 0
  lote=$(AWSL s3api list-object-versions --bucket "$b" --output json | jq -c '{Objects: [((.Versions // [])[], (.DeleteMarkers // [])[]) | {Key, VersionId}], Quiet: true}')
  [ "$(echo "$lote" | jq '.Objects | length')" = 0 ] || AWSL s3api delete-objects --bucket "$b" --delete "$lote" >/dev/null
  AWSL s3api delete-bucket --bucket "$b"
}
limpiar_residuos() { # una ejecución interrumpida deja recursos en LocalStack sin estado que los conozca: se borran para partir de cero
  local amb
  for amb in pruebas produccion; do
    AWSL s3 rb "s3://pedidos-$amb-archivos" --force >/dev/null 2>&1 || true
    AWSL iam delete-role-policy --role-name "pedidos-$amb-aplicacion" --policy-name archivos >/dev/null 2>&1 || true
    AWSL iam delete-role --role-name "pedidos-$amb-aplicacion" >/dev/null 2>&1 || true
    AWSL secretsmanager delete-secret --secret-id "pedidos-$amb-bd" --force-delete-without-recovery >/dev/null 2>&1 || true
  done
}
comprobar "LocalStack responde" curl -sf "$ENDPOINT/_localstack/health" -o /dev/null
limpiar_residuos
cd "$TRABAJO/infra"
rm -rf terraform.tfstate terraform.tfstate.backup terraform.tfstate.d .terraform/terraform.tfstate   # desde cero: LocalStack arranca vacío

paso "Estado remoto con bloqueo (S3 en LocalStack): el estado no vive en el disco de nadie"
borrar_bucket_versionado tofu-estado
AWSL s3api create-bucket --bucket tofu-estado >/dev/null
AWSL s3api put-bucket-versioning --bucket tofu-estado --versioning-configuration Status=Enabled
tofu init -input=false -reconfigure -backend-config=backend.hcl >/dev/null
tofu workspace select -or-create pruebas >/dev/null

paso "4-5. Infraestructura mínima y dos ambientes con el mismo código y variables distintas"
for amb in pruebas produccion; do
  tofu workspace select -or-create "$amb" >/dev/null
  tofu apply -auto-approve -input=false -var-file="$amb.tfvars" >/dev/null
  comprobar "ambiente $amb: bucket pedidos-$amb-archivos, rol y secreto creados" bash -c "
    aws --endpoint-url $ENDPOINT s3api head-bucket --bucket pedidos-$amb-archivos &&
    aws --endpoint-url $ENDPOINT iam get-role --role-name pedidos-$amb-aplicacion >/dev/null &&
    aws --endpoint-url $ENDPOINT secretsmanager get-secret-value --secret-id pedidos-$amb-bd --query SecretString --output text | grep -q ^clave-"
done
comprobar "el estado de ambos ambientes está en el bucket tofu-estado" bash -c "aws --endpoint-url $ENDPOINT s3 ls s3://tofu-estado/ --recursive | grep -c tfstate | grep -qx 2"
comprobar "no queda ningún terraform.tfstate en el disco" bash -c "! ls '$TRABAJO'/infra/terraform.tfstate* >/dev/null 2>&1"
comprobar "el secreto de la base no está en ningún archivo del directorio de trabajo (solo en el estado remoto)" bash -c "! grep -rlF '$TF_VAR_clave_bd' '$TRABAJO/infra'"
v_pruebas=$(AWSL s3api get-bucket-versioning --bucket pedidos-pruebas-archivos --query Status --output text)
v_prod=$(AWSL s3api get-bucket-versioning --bucket pedidos-produccion-archivos --query Status --output text)
comprobar "las variables distintas producen ambientes distintos (versionado: pruebas=$v_pruebas, producción=$v_prod)" test "$v_pruebas|$v_prod" = "None|Enabled" -o "$v_pruebas|$v_prod" = "Suspended|Enabled"
comprobar "los estados son independientes (un workspace por ambiente)" test "$(tofu workspace list | grep -c -E 'pruebas|produccion')" = 2
comprobar "el rol solo permite el bucket de su ambiente (mínimo privilegio)" bash -c "AWS_ACCESS_KEY_ID=test aws --endpoint-url $ENDPOINT iam get-role-policy --role-name pedidos-produccion-aplicacion --policy-name archivos --query PolicyDocument | grep -q 'pedidos-produccion-archivos' && ! aws --endpoint-url $ENDPOINT iam get-role-policy --role-name pedidos-produccion-aplicacion --policy-name archivos --query PolicyDocument | grep -q 'pedidos-pruebas'"

paso "Aplicar el tfvars equivocado en un workspace falla (precondición)"
tofu workspace select pruebas >/dev/null
comprobar "pruebas.tfvars no se puede aplicar a produccion y al revés: el plan se rechaza" bash -c "cd '$TRABAJO/infra' && ! tofu plan -input=false -var-file=produccion.tfvars >'$TRABAJO/precondicion.txt' 2>&1 && grep -q 'no coincide con el ambiente' '$TRABAJO/precondicion.txt'"

paso "El bloqueo del estado: un segundo plan concurrente falla"
tofu workspace select produccion >/dev/null
( sleep 20; echo no ) | tofu apply -input=true -var-file=produccion.tfvars -var versionado=false >"$TRABAJO/apply-bloqueado.txt" 2>&1 &
retenedor=$!
sleep 8
comprobar "mientras un apply espera confirmación, existe el archivo de bloqueo en el bucket" bash -c "aws --endpoint-url $ENDPOINT s3 ls s3://tofu-estado/ --recursive | grep -q tflock"
comprobar "un segundo plan concurrente falla con 'Error acquiring the state lock'" bash -c "cd '$TRABAJO/infra' && ! tofu plan -input=false -lock-timeout=0s -var-file=produccion.tfvars >'$TRABAJO/bloqueo.txt' 2>&1 && grep -q 'Error acquiring the state lock' '$TRABAJO/bloqueo.txt'"
wait $retenedor || true
comprobar "al terminar el primero, el bloqueo se libera y el plan vuelve a funcionar" bash -c "cd '$TRABAJO/infra' && tofu plan -input=false -lock-timeout=30s -var-file=produccion.tfvars >/dev/null"

paso "6. Deriva: se cambia algo a mano y plan la detecta"
tofu workspace select produccion >/dev/null
tofu plan -detailed-exitcode -input=false -var-file=produccion.tfvars >/dev/null && c0=0 || c0=$?
comprobar "sin cambios manuales, plan sale con código 0 ($c0)" test "$c0" = 0
AWSL s3api put-bucket-versioning --bucket pedidos-produccion-archivos --versioning-configuration Status=Suspended
AWSL s3api put-bucket-tagging --bucket pedidos-produccion-archivos --tagging 'TagSet=[{Key=app,Value=pedidos},{Key=ambiente,Value=produccion},{Key=manual,Value=si}]'
tofu plan -detailed-exitcode -input=false -var-file=produccion.tfvars > "$TRABAJO/plan-deriva.txt" 2>&1 && c1=0 || c1=$?
comprobar "tras el cambio manual, plan sale con código 2 (hay diferencias)" test "$c1" = 2
grep -E "versioning_configuration|status|manual" "$TRABAJO/plan-deriva.txt" | sed 's/^/    /' | head -8
comprobar "el plan muestra el versionado y la etiqueta manual" bash -c "grep -q 'Suspended' '$TRABAJO/plan-deriva.txt' && grep -q manual '$TRABAJO/plan-deriva.txt'"
tofu apply -auto-approve -input=false -var-file=produccion.tfvars >/dev/null
tofu plan -detailed-exitcode -input=false -var-file=produccion.tfvars >/dev/null && c2=0 || c2=$?
comprobar "aplicar corrige la deriva y plan vuelve a 0" test "$c2" = 0
comprobar "el versionado volvió a Enabled" test "$(AWSL s3api get-bucket-versioning --bucket pedidos-produccion-archivos --query Status --output text)" = Enabled

paso "7. Destruir y reconstruir desde cero (medido)"
t0=$SECONDS; tofu destroy -auto-approve -input=false -var-file=produccion.tfvars >/dev/null; t_destruir=$((SECONDS - t0))
comprobar "tras destruir, el bucket no existe" bash -c "! aws --endpoint-url $ENDPOINT s3api head-bucket --bucket pedidos-produccion-archivos 2>/dev/null"
t0=$SECONDS; tofu apply -auto-approve -input=false -var-file=produccion.tfvars >/dev/null; t_reconstruir=$((SECONDS - t0))
comprobar "tras reconstruir, el ambiente es el mismo (plan sin cambios)" bash -c "tofu plan -detailed-exitcode -input=false -var-file=produccion.tfvars >/dev/null"
anotar "tofu_destruir_s=$t_destruir tofu_reconstruir_s=$t_reconstruir (LocalStack; en AWS real una base RDS por sí sola tarda 5-15 min)"

paso "El fragmento del README (aws_db_instance + Secrets Manager): validar y planificar"
extraer_bloque "$AQUI/../../README.md" 1 hcl > "$TRABAJO/iac-readme/readme.tf"; test -s "$TRABAJO/iac-readme/readme.tf"
cd "$TRABAJO/iac-readme"
tofu init -input=false >/dev/null; comprobar "tofu validate del fragmento del README" tofu validate
tofu plan -input=false -var ambiente=pruebas > "$TRABAJO/plan-readme.txt" 2>&1 || true
comprobar "plan del fragmento: crea la base y el secreto (1 instancia + 2 recursos de secreto)" grep -q "3 to add" "$TRABAJO/plan-readme.txt"
anotar "rds_en_localstack_community=no_disponible (el plan funciona, el apply de aws_db_instance no)"

paso "2. Sin estado local: sesiones en Redis y archivos en S3, con dos instancias"
cd "$TRABAJO/infra"; tofu workspace select pruebas >/dev/null
export APP_ARCHIVOS_BUCKET=pedidos-pruebas-archivos APP_ARCHIVOS_ENDPOINT=http://localhost:$PUERTO_LOCALSTACK
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$PUERTO_PG/tienda SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=local
arrancar() { # arrancar puerto [perfil]
  SERVER_PORT=$1 SPRING_PROFILES_ACTIVE=${2:-} REDIS_PUERTO=$PUERTO_REDIS java -jar "$JAR" > "$TRABAJO/app-$1.log" 2>&1 & PIDS+=($!)
  esperar_http "http://localhost:$1/actuator/health/readiness" 90 || { tail -30 "$TRABAJO/app-$1.log"; falla "la instancia $1 no arrancó"; }
}
parar() { for p in "${PIDS[@]:-}"; do [ -n "$p" ] && kill "$p" 2>/dev/null || true; done; PIDS=(); sleep 2; }
visitas() { curl -s -b "$1" -c "$1" "localhost:$2/api/visitas" | jq -r .visitas; }
CJ=$TRABAJO/cookies.txt

arrancar $PUERTO_A; arrancar $PUERTO_B
rm -f "$CJ"; a=$(visitas "$CJ" $PUERTO_A); b=$(visitas "$CJ" $PUERTO_B); c=$(visitas "$CJ" $PUERTO_A)
anotar "sin_redis_visitas_instancia_A,B,A=$a,$b,$c"
comprobar "problema: con la sesión local, al cambiar de instancia la sesión se pierde ($a,$b,$c en lugar de 1,2,3)" test "$a,$b,$c" = "1,1,1"
parar
arrancar $PUERTO_A redis; arrancar $PUERTO_B redis
rm -f "$CJ"; a=$(visitas "$CJ" $PUERTO_A); b=$(visitas "$CJ" $PUERTO_B); c=$(visitas "$CJ" $PUERTO_A)
anotar "con_redis_visitas_instancia_A,B,A=$a,$b,$c"
comprobar "con Redis la sesión es compartida (1,2,3)" test "$a,$b,$c" = "1,2,3"
echo "contenido-$RANDOM" > "$TRABAJO/informe.txt"
curl -sf -F "archivo=@$TRABAJO/informe.txt" localhost:$PUERTO_A/api/archivos >/dev/null
comprobar "un archivo subido por la instancia A lo baja la instancia B (S3)" bash -c "[ \"\$(curl -s localhost:$PUERTO_B/api/archivos/informe.txt)\" = \"\$(cat $TRABAJO/informe.txt)\" ]"
comprobar "el archivo está en el almacenamiento de objetos, no en el disco de la instancia" bash -c "aws --endpoint-url $ENDPOINT s3 ls s3://pedidos-pruebas-archivos/ | grep -q informe.txt"
parar

paso "3. Configuración y secretos fuera del repositorio"
# El YAML del README (BD_URL, BD_USUARIO, BD_CLAVE) vive en el perfil "doce-factores" del proyecto.
env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD -u APP_ARCHIVOS_BUCKET -u APP_ARCHIVOS_ENDPOINT \
  BD_URL=jdbc:postgresql://localhost:$PUERTO_PG/tienda BD_USUARIO=postgres BD_CLAVE=local \
  SERVER_PORT=$PUERTO_DOCE SPRING_PROFILES_ACTIVE=doce-factores java -jar "$JAR" > "$TRABAJO/app-doce-factores.log" 2>&1 & PIDS+=($!)
comprobar "el perfil con \${BD_URL}/\${BD_USUARIO}/\${BD_CLAVE} del README arranca y conecta" esperar_http http://localhost:$PUERTO_DOCE/actuator/health/readiness 90
parar
comprobar "Trivy no encuentra secretos en el código de la aplicación (el repositorio podría ser público)" bash -c "trivy fs -q --cache-dir $TRIVY_CACHE --scanners secret --exit-code 1 --skip-dirs target $AQUI/aplicacion > $TRABAJO/trivy-secretos.txt 2>&1 || { tail -5 $TRABAJO/trivy-secretos.txt; exit 1; }"
comprobar "ninguna clave AWS ni contraseña literal en application*.yml" bash -c "! grep -rniE '(password|secret|access.?key)\s*[:=]\s*[^\$ ]' $AQUI/aplicacion/src/main/resources"
comprobar "la contraseña de la base en OpenTofu llega por TF_VAR, no por archivo" bash -c "! grep -rn 'clave-' --include=*.tf --include=*.tfvars $TRABAJO/infra"

AWSL s3 rm s3://pedidos-pruebas-archivos --recursive >/dev/null
cd "$TRABAJO/infra"; for amb in pruebas produccion; do tofu workspace select "$amb" >/dev/null; tofu destroy -auto-approve -input=false -var-file="$amb.tfvars" >/dev/null; done
comprobar "ambientes destruidos: solo queda el bucket del estado" bash -c "[ \"\$(aws --endpoint-url $ENDPOINT s3 ls | awk '{print \$3}')\" = tofu-estado ]"
borrar_bucket_versionado tofu-estado

echo; echo "Taller 05: todo correcto"
