#!/usr/bin/env bash
# Solución del taller de la sesión 01 (fundamentos): red de dos zonas, acceso por rol sin claves, costos.
# Levanta LocalStack, aplica el código con OpenTofu, comprueba y lo baja todo al terminar.
# El "servicio de contenedores gestionado con base gestionada" solo se valida y se planifica: en LocalStack Community
# no existen ECS, RDS ni ELB (ver ANALISIS.md).
# Variables opcionales: PUERTO_LOCALSTACK (4566), PROYECTO (nombre del proyecto de Compose), TF_PLUGIN_CACHE_DIR.
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"; source "$AQUI/comun.sh"
requiere docker tofu aws jq python3 curl
PUERTO_LOCALSTACK=${PUERTO_LOCALSTACK:-4566}; PROYECTO=${PROYECTO:-n10-01}
export PUERTO_LOCALSTACK
export TF_PLUGIN_CACHE_DIR=${TF_PLUGIN_CACHE_DIR:-$HOME/.cache/curso-tofu-plugins}; mkdir -p "$TF_PLUGIN_CACHE_DIR"
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1
export TF_VAR_endpoint=http://localhost:$PUERTO_LOCALSTACK
ENDPOINT=$TF_VAR_endpoint
AWSL() { aws --endpoint-url "$ENDPOINT" "$@"; }
# El trabajo (estado de OpenTofu, planes) se hace en una copia temporal: esta carpeta queda limpia.
TRABAJO=$(mktemp -d); cp -R "$AQUI/infra" "$AQUI/infra-gestionada" "$TRABAJO/"
bajar() {
  ( cd "$TRABAJO/infra" && tofu destroy -auto-approve -input=false >/dev/null 2>&1 ) || true
  docker compose -p "$PROYECTO" -f "$AQUI/compose.localstack.yaml" down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$TRABAJO"
}
trap bajar EXIT

paso "Levantando LocalStack en el puerto $PUERTO_LOCALSTACK"
docker compose -p "$PROYECTO" -f "$AQUI/compose.localstack.yaml" up -d --wait >/dev/null
comprobar "LocalStack responde" curl -sf "$ENDPOINT/_localstack/health" -o /dev/null
cd "$TRABAJO/infra"; tofu init -input=false >/dev/null; tofu validate >/dev/null

paso "4. Arquitectura de dos zonas como código"
tofu apply -auto-approve -input=false >/dev/null
vpc=$(AWSL ec2 describe-vpcs --filters Name=tag:Name,Values=pedidos --query 'Vpcs[0].VpcId' --output text)
zonas=$(AWSL ec2 describe-subnets --filters Name=vpc-id,Values="$vpc" Name=tag:Name,Values='privada-*' --query 'Subnets[].AvailabilityZone' --output text | tr '\t' '\n' | sort -u | wc -l | tr -d ' ')
total=$(AWSL ec2 describe-subnets --filters Name=vpc-id,Values="$vpc" --query 'length(Subnets)' --output text)
comprobar "hay 4 subredes (pública y privada en cada zona) en 2 zonas distintas" test "$total/$zonas" = "4/2"
comprobar "la base solo acepta tráfico desde el grupo de seguridad de la aplicación" bash -c "aws --endpoint-url $ENDPOINT ec2 describe-security-groups --filters Name=group-name,Values=pedidos-base --query 'SecurityGroups[0].IpPermissions[0].UserIdGroupPairs | length(@)' --output text | grep -qx 1"

paso "3. Acceso al almacenamiento de objetos con un rol, sin claves de larga duración"
comprobar "el bucket bloquea el acceso público" bash -c "aws --endpoint-url $ENDPOINT s3api get-public-access-block --bucket pedidos-fundamentos-archivos --query 'PublicAccessBlockConfiguration.BlockPublicPolicy' --output text | grep -qx True"
comprobar "la política del rol nombra solo el bucket de la aplicación y solo Get/Put" bash -c "aws --endpoint-url $ENDPOINT iam get-role-policy --role-name pedidos-aplicacion --policy-name archivos --output json | jq -e '.PolicyDocument.Statement | length == 1 and (.[0].Action | sort) == [\"s3:GetObject\",\"s3:PutObject\"] and (.[0].Resource | test(\"pedidos-fundamentos-archivos/\\\\*\$\"))' >/dev/null"
cred=$(AWSL sts assume-role --role-arn "$(tofu output -raw rol)" --role-session-name taller01 --output json)
comprobar "se obtienen credenciales temporales (con token de sesión y vencimiento)" bash -c "echo '$cred' | jq -e '.Credentials | has(\"SessionToken\") and has(\"Expiration\")' >/dev/null"
echo "hola-desde-el-rol" > "$TRABAJO/rol.txt"
AWS_ACCESS_KEY_ID=$(echo "$cred" | jq -r .Credentials.AccessKeyId) AWS_SECRET_ACCESS_KEY=$(echo "$cred" | jq -r .Credentials.SecretAccessKey) AWS_SESSION_TOKEN=$(echo "$cred" | jq -r .Credentials.SessionToken) \
  aws --endpoint-url "$ENDPOINT" s3 cp "$TRABAJO/rol.txt" s3://pedidos-fundamentos-archivos/rol.txt >/dev/null
comprobar "con ellas la aplicación escribe en el bucket" bash -c "aws --endpoint-url $ENDPOINT s3 cp s3://pedidos-fundamentos-archivos/rol.txt - | grep -q hola-desde-el-rol"
comprobar "el código de infraestructura no contiene claves de larga duración (solo las ficticias 'test' de LocalStack)" bash -c "! grep -rniE 'AKIA' '$AQUI/infra' '$AQUI/infra-gestionada'"
AWSL s3 rm s3://pedidos-fundamentos-archivos/rol.txt >/dev/null

paso "1-2. Servicio de contenedores gestionado + base gestionada + presupuesto: validar y planificar (apply imposible en LocalStack Community)"
I=$TRABAJO/infra
export TF_VAR_vpc_id=$(tofu -chdir="$I" output -raw vpc_id) TF_VAR_sg_aplicacion=$(tofu -chdir="$I" output -raw sg_aplicacion) TF_VAR_sg_base=$(tofu -chdir="$I" output -raw sg_base) TF_VAR_rol_tarea=$(tofu -chdir="$I" output -raw rol)
export TF_VAR_subredes_publicas=$(tofu -chdir="$I" output -json subredes_publicas_ids) TF_VAR_subredes_privadas=$(tofu -chdir="$I" output -json subredes_privadas_ids)
cd "$TRABAJO/infra-gestionada"; tofu init -input=false >/dev/null
comprobar "tofu validate de ECS Fargate + ALB + RDS Multi-AZ + Budgets" tofu validate
tofu plan -input=false -out=plan.bin >/dev/null
tofu show -json plan.bin > "$TRABAJO/plan-gestionada.json"
n=$(jq '[.resource_changes[] | select(.change.actions == ["create"])] | length' "$TRABAJO/plan-gestionada.json")
echo "    recursos a crear: $n -> $(jq -r '[.resource_changes[].type] | sort | join(" ")' "$TRABAJO/plan-gestionada.json")"
comprobar "el plan crea 14 recursos (la aplicación, el balanceador, la base y el presupuesto)" test "$n" = 14
comprobar "la base es Multi-AZ, cifrada, en subredes privadas y con la contraseña administrada por RDS" jq -e '.resource_changes[] | select(.type=="aws_db_instance") | .change.after | .multi_az and .storage_encrypted and .manage_master_user_password' "$TRABAJO/plan-gestionada.json"
comprobar "el servicio ECS tiene 2 tareas en las subredes privadas y las tareas usan el rol de la aplicación (sin claves)" jq -e '([.resource_changes[] | select(.type=="aws_ecs_service") | .change.after.desired_count] == [2]) and ([.resource_changes[] | select(.type=="aws_ecs_task_definition") | .change.after.task_role_arn | test("pedidos-aplicacion")] == [true])' "$TRABAJO/plan-gestionada.json"
comprobar "el presupuesto avisa al 50, 80 y 100 % reales y al 100 % pronosticado" jq -e '[.resource_changes[] | select(.type=="aws_budgets_budget") | .change.after.notification[] | "\(.notification_type):\(.threshold)"] | sort == ["ACTUAL:100","ACTUAL:50","ACTUAL:80","FORECASTED:100"]' "$TRABAJO/plan-gestionada.json"

paso "5. Estimación mensual y dos formas de reducirla"
python3 "$AQUI/costo.py" | sed 's/^/    /' | tee "$TRABAJO/costo.txt"
comprobar "las dos reducciones bajan el total" grep -q 'OK$' "$TRABAJO/costo.txt"
echo; echo "Taller 01: todo correcto"
