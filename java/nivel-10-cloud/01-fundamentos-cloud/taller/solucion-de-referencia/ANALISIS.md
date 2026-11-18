# Taller 10-01: fundamentos de la nube (solución en local)

Todo corre en local: LocalStack 4.14.0 en `localhost:4566` y OpenTofu 1.13.9 (código en `infra/main.tf`, ejecutado por `taller.sh`). Las diferencias con AWS real están al final de este documento.

## 1. Proveedor, capa gratuita y alertas de presupuesto

Proveedor elegido: AWS. Alertas antes de crear nada: un presupuesto mensual (`aws_budgets_budget`) con avisos al 50 %, 80 % y 100 % reales y al 100 % pronosticado, y etiquetas `app` y `ambiente` en todo recurso para atribuir costos. AWS Budgets no existe en LocalStack Community, así que el código (en `infra-gestionada/main.tf`) se **validó y se planificó** (`tofu validate` y `tofu plan -out`; el plan en JSON confirma las cuatro notificaciones), pero **no se aplicó**: eso exige una cuenta real.

## 2. Aplicación en contenedores gestionados con base gestionada

Dos entregables:

- **Ejecutado:** el equivalente local es el Deployment de kind con PostgreSQL de los talleres 10-02 y 10-03 (3 réplicas, sondas, `Secret` para la clave).
- **Validado y planificado, no aplicado:** `infra-gestionada/main.tf` describe el servicio real: ECS Fargate (clúster, definición de tarea con `task_role_arn` igual al rol del punto 3, servicio con 2 tareas en las subredes privadas), un balanceador (ALB) en las subredes públicas con la sonda `/actuator/health/readiness`, una base **RDS PostgreSQL Multi-AZ** cifrada en las subredes privadas con la contraseña administrada por RDS (`manage_master_user_password`, sin pasar por el código), y el presupuesto del punto 1. Reutiliza la red, los grupos de seguridad y el rol creados por `infra/` en LocalStack (se le pasan como variables). `taller.sh` ejecuta `tofu validate` y `tofu plan` y comprueba sobre el plan en JSON: 14 recursos a crear, base Multi-AZ y cifrada, 2 tareas, rol de tarea sin claves y las cuatro alertas. Lo que **no** se pudo comprobar es el `apply`: ECS, ELB y RDS no existen en LocalStack Community (ver las diferencias al final).

## 3. Acceso a objetos con un rol, sin claves

`taller.sh` crea el bucket (acceso público bloqueado), el rol `pedidos-aplicacion` con una política de solo `s3:GetObject` y `s3:PutObject` sobre `bucket/*`, obtiene credenciales **temporales** con `sts assume-role` y escribe un objeto con ellas. El código Java (`ControladorArchivos`) no contiene claves: usa la cadena por defecto del SDK. LocalStack Community no hace cumplir las políticas IAM, así que no se pudo demostrar una denegación; la política se comprueba de forma estática.

## 4. Arquitectura con dos zonas e identificación de puntos únicos de fallo

```
                      Internet
                         │
              ┌──────────┴──────────┐
              │ Balanceador (ALB)   │   (gestionado, multi-zona)
              └───────┬─────┬───────┘
        zona A        │     │        zona B
 subred pública ──────┘     └────── subred pública
 subred privada: app (sin estado)    subred privada: app (sin estado)
        │                                   │
        └───────────┬───────────────────────┘
            RDS PostgreSQL  (primaria en A, réplica síncrona en B)
            Caché de sesiones (nodo primario en A, réplica en B)
            S3 (regional, multi-zona)
```

Puntos únicos de fallo que quedan:

| Punto | Por qué es único | Mitigación |
|---|---|---|
| La base primaria | La conmutación por error tarda de 60 a 120 s y corta escrituras | Multi-AZ; la aplicación reintenta con retroceso |
| Una sola NAT (si se ahorra) | Una zona caída deja sin salida a la otra | Una NAT por zona en producción |
| La región entera | Todo vive en una región | Respaldos entre regiones; plan de recuperación con RTO y RPO explícitos |
| El DNS y la cuenta del proveedor | Fuera del control del equipo | Credenciales protegidas con MFA, copia de la configuración (IaC) |
| El propio código/configuración | Un despliegue defectuoso se replica en ambas zonas | Despliegue progresivo y reversa (taller 10-03) |
| La sesión si no es externa | Sesión local = perder sesiones al caer la instancia | Sesiones en Redis (taller 10-05) |

## 5. Costo mensual (script `costo.py`)

Precios de lista aproximados, **supuestos** a confirmar en la calculadora del proveedor.

| Concepto | USD/mes |
|---|---|
| Contenedores (2 tareas de 0,5 vCPU y 1 GB) | 36,04 |
| Balanceador | 22,27 |
| Base de datos (t4g.micro, 50 GB, Multi-AZ) | 34,86 |
| 2 NAT | 65,70 |
| S3 (100 GB) | 2,30 |
| Salida de 200 GB | 18,00 |
| **Total** | **179,17** |

Dos formas de reducirlo: (1) **una sola NAT** en los ambientes que toleren perder la salida de una zona: baja a 146,32 (-32,85); (2) **Fargate Spot** para tareas tolerantes a interrupciones (-58,08 en total, 121,09). Extra: apagar el ambiente de pruebas fuera de horario laboral reduce su cómputo a 6,44 USD.

## 6. IaaS, contenedores gestionados y *serverless* para esta aplicación

| | IaaS (EC2) | Contenedores gestionados (Fargate/EKS) | *Serverless* (Lambda) |
|---|---|---|---|
| Esfuerzo operativo | Alto: parches del sistema, escalado, agentes | Medio: imagen y manifiestos; el proveedor opera los nodos (Fargate) | Bajo: solo el código |
| Costo con carga estable | El más bajo con instancias reservadas | Medio | Alto con tráfico constante; el mejor con picos y largos periodos en cero |
| Límites | Ninguno impuesto | Arranque de decenas de segundos al escalar | Arranque en frío de una JVM de 2-5 s (SnapStart o nativo lo mitigan), 15 min máx. por invocación, sin conexiones persistentes a la base (necesita proxy) |
| Encaje con `pedidos` | Posible, pero sin ventaja | **Mejor encaje:** ya es una imagen con sondas y apagado ordenado | Solo para trabajos puntuales (informes, procesamiento de eventos) |

Recomendación: contenedores gestionados para la API; *serverless* para tareas asíncronas esporádicas.

Nota sobre el estado: en este taller el estado de OpenTofu (`terraform.tfstate`) queda en disco local (`taller.sh` trabaja en una copia temporal y la borra al terminar), porque es un ejercicio de un solo usuario contra LocalStack y no contiene secretos. Un equipo no debe hacerlo: la sesión 05 resuelve el estado remoto con bloqueo (backend S3 con `use_lockfile`) y lo comprueba con dos planes concurrentes.

## Diferencias entre LocalStack y AWS real

| Tema | LocalStack Community 4.14.0 | AWS real |
|---|---|---|
| Servicios | Disponibles: S3, IAM, STS, Secrets Manager, EC2 (metadatos), DynamoDB, KMS, Kinesis, Lambda. **No disponibles:** RDS, ECS, EKS, ELB, ElastiCache, Budgets, CloudWatch | Todos |
| RDS | `tofu plan` del `aws_db_instance` funciona; `apply` no | Una base Multi-AZ tarda de 10 a 20 minutos en crearse y destruirse |
| IAM | Se crean roles y políticas y `assume-role` entrega credenciales, pero las políticas **no se evalúan**: no se puede demostrar una denegación | Se aplica siempre; una denegación explícita gana a un permiso |
| Credenciales | Claves ficticias `test/test` en el proveedor | Rol de la tarea o identidad federada (OIDC); sin claves de larga duración |
| Red (EC2/VPC) | Las subredes y grupos de seguridad son registros, sin tráfico real ni zonas de disponibilidad reales | Zonas físicamente separadas; los grupos de seguridad filtran tráfico |
| Tiempos | Crear, destruir y reconstruir un ambiente toma segundos | Minutos (RDS, balanceadores, NAT) |
