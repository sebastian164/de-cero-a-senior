# 05 — Aplicaciones de doce factores e infraestructura como código

## Idea

Un electrodoméstico bien diseñado funciona en cualquier casa: se enchufa a una toma estándar y no asume nada del cableado. Una aplicación nativa de la nube funciona en cualquier plataforma porque sigue unas convenciones. Y la infraestructura como código es tener el plano eléctrico de la casa por escrito, de modo que se pueda reconstruir idéntica.

## Lo esencial

### Los doce factores

| # | Factor | En la práctica |
|---|---|---|
| 1 | Base de código | Un repositorio, muchos despliegues |
| 2 | Dependencias | Declaradas y aisladas (`pom.xml`); nada instalado a mano en el servidor |
| 3 | Configuración | En el entorno, no en el código |
| 4 | Servicios de respaldo | Base, caché y colas son recursos conectables por configuración |
| 5 | Construir, publicar, ejecutar | Etapas estrictamente separadas |
| 6 | Procesos | Sin estado; el estado va a un servicio de respaldo |
| 7 | Vinculación de puertos | La aplicación expone su propio puerto |
| 8 | Concurrencia | Se escala con más procesos |
| 9 | Desechabilidad | Arranque rápido y apagado ordenado |
| 10 | Paridad entre ambientes | Desarrollo, pruebas y producción lo más parecidos posible |
| 11 | Registros | Flujo de eventos a la salida estándar |
| 12 | Procesos de administración | Tareas puntuales como procesos de una sola ejecución, con el mismo código |

Los que más se incumplen: el 3 (configuración incrustada), el 6 (sesiones o archivos en memoria o disco local) y el 10 (H2 en desarrollo, PostgreSQL en producción).

### Configuración y secretos

- La configuración varía por ambiente; el código no.
- Los secretos no van en el repositorio ni en la imagen. Se inyectan en ejecución desde un gestor (Vault, el gestor de secretos del proveedor), con rotación y auditoría.
- Prueba de fuego: ¿podría hacerse público el repositorio hoy mismo sin exponer ninguna credencial?

### Infraestructura como código (IaC)

La infraestructura se define en archivos versionados, revisados y aplicados de forma automática.

| Herramienta | Enfoque |
|---|---|
| Terraform / OpenTofu | Declarativo, multiproveedor |
| CloudFormation, Bicep | Declarativo, propio de un proveedor |
| Pulumi, CDK | Con lenguajes de programación |
| Helm, Kustomize | Para manifiestos de Kubernetes |

Beneficios: ambientes reproducibles, cambios revisables en un *pull request*, historial, recuperación ante desastres y fin del "funciona en mi cuenta".

Conceptos: **estado** (lo que la herramienta cree que existe; se guarda de forma remota y con bloqueo), **plan** (qué cambiaría) y **aplicar**. **Deriva:** cuando alguien cambia algo a mano y la realidad deja de coincidir con el código.

**GitOps:** el repositorio es la fuente de verdad del estado deseado, y un agente (Argo CD, Flux) lo aplica al clúster de forma continua.

## Ejemplo

```hcl
resource "aws_db_instance" "pedidos" {
  identifier              = "pedidos-${var.ambiente}"
  engine                  = "postgres"
  engine_version          = "16"
  instance_class          = var.ambiente == "prod" ? "db.m6g.large" : "db.t4g.micro"
  allocated_storage       = 50
  multi_az                = var.ambiente == "prod"
  backup_retention_period = 7
  storage_encrypted       = true
  publicly_accessible     = false
  username                = "pedidos"
  password                = data.aws_secretsmanager_secret_version.pedidos_db.secret_string
}
```

El mismo código crea el ambiente de pruebas y el de producción; cambian las variables.

```yaml
# Spring: configuración por entorno, sin valores sensibles en el archivo
spring:
  datasource:
    url: ${BD_URL}
    username: ${BD_USUARIO}
    password: ${BD_CLAVE}
```

## Errores comunes

- Sesiones HTTP o archivos subidos guardados en la instancia.
- Perfiles con credenciales en el repositorio.
- Infraestructura creada desde la consola web y nunca documentada.
- Cambios manuales "urgentes" que provocan deriva.
- Estado de Terraform en el disco de una persona o con secretos sin proteger.
- Ambientes de prueba que no se parecen en nada a producción.
- Aplicar cambios de infraestructura sin revisar el plan.
- Tareas administrativas ejecutadas a mano conectándose al servidor.

## Taller

1. Evalúa una aplicación tuya contra los doce factores. Para cada incumplimiento, propón la corrección.
2. Elimina todo estado local: mueve las sesiones a un almacén externo y los archivos a almacenamiento de objetos.
3. Externaliza toda la configuración y los secretos. Comprueba que el repositorio podría ser público.
4. Define con Terraform u OpenTofu la infraestructura mínima de la aplicación (puedes usar un emulador local como LocalStack).
5. Crea dos ambientes con el mismo código y variables distintas.
6. Cambia algo a mano y detecta la deriva con `plan`.
7. Destruye el ambiente y reconstrúyelo desde cero. Mide cuánto tarda.

Rama `taller/n10-05-doce-factores-iac`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Por qué una aplicación debe ser sin estado para escalar?
2. ¿Cómo gestionas la configuración y los secretos entre ambientes?
3. ¿Qué problemas resuelve la infraestructura como código?
4. ¿Qué es la deriva de configuración y cómo se evita?
5. ¿Qué es GitOps?
6. ¿Cuánto tardaría tu equipo en reconstruir producción desde cero y de qué depende?
