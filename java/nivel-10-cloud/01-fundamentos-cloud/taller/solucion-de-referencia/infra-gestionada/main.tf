# Punto 2 del taller: la aplicación en un servicio de contenedores gestionado (ECS Fargate) detrás de un balanceador,
# con una base de datos gestionada (RDS PostgreSQL Multi-AZ), y el presupuesto con alertas del punto 1.
# Reutiliza la red, los grupos de seguridad y el rol de ../infra (se pasan como variables).
# LocalStack Community no incluye ECS, ELB ni RDS: aquí solo se puede VALIDAR y PLANIFICAR (no aplicar). Ver ANALISIS.md.
terraform {
  required_providers {
    aws = { source = "hashicorp/aws", version = "6.21.0" }
  }
}

variable "vpc_id" { type = string }
variable "subredes_publicas" { type = list(string) }
variable "subredes_privadas" { type = list(string) }
variable "sg_aplicacion" { type = string }
variable "sg_base" { type = string }
variable "rol_tarea" { type = string } # el rol de ../infra: la aplicación accede a S3 sin claves
variable "imagen" {
  type    = string
  default = "registro.example/pedidos:1.4.2"
}

# Para planificar sin cuenta: ni credenciales reales ni llamadas a AWS. En una cuenta real se quitan estas líneas.
provider "aws" {
  region                      = "us-east-1"
  access_key                  = "test"
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true
}

locals { etiquetas = { app = "pedidos", ambiente = "produccion", gestionado-por = "opentofu" } }

# --- Base gestionada: Multi-AZ en las subredes privadas; la contraseña la crea y rota RDS en Secrets Manager (no pasa por el código).
resource "aws_db_subnet_group" "base" {
  name       = "pedidos"
  subnet_ids = var.subredes_privadas
  tags       = local.etiquetas
}
resource "aws_db_instance" "base" {
  identifier                  = "pedidos"
  engine                      = "postgres"
  engine_version              = "16"
  instance_class              = "db.t4g.micro"
  allocated_storage           = 20
  db_name                     = "tienda"
  username                    = "pedidos"
  manage_master_user_password = true
  multi_az                    = true # un fallo de zona promueve la réplica sin intervención
  db_subnet_group_name        = aws_db_subnet_group.base.name
  vpc_security_group_ids      = [var.sg_base]
  backup_retention_period     = 7
  storage_encrypted           = true
  deletion_protection         = true
  skip_final_snapshot         = false
  final_snapshot_identifier   = "pedidos-final"
  tags                        = local.etiquetas
}

# --- Balanceador en las subredes públicas.
resource "aws_security_group" "balanceador" {
  name   = "pedidos-balanceador"
  vpc_id = var.vpc_id
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }
  tags = local.etiquetas
}
resource "aws_lb" "principal" {
  name               = "pedidos"
  load_balancer_type = "application"
  subnets            = var.subredes_publicas
  security_groups    = [aws_security_group.balanceador.id]
  tags               = local.etiquetas
}
resource "aws_lb_target_group" "aplicacion" {
  name        = "pedidos"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"
  health_check { path = "/actuator/health/readiness" } # la misma sonda de readiness que usa Kubernetes
}
# Solo HTTP en el 80 para poder validar y planificar sin un dominio. En producción el listener sería HTTPS en el 443 (aws_lb_listener con certificate_arn de ACM
# y una política TLS moderna) y el del 80 solo redirigiría al 443 (default_action type = "redirect", status_code = "HTTP_301").
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.principal.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.aplicacion.arn
  }
}

# --- Aplicación en ECS Fargate: dos tareas, una por zona; rol de tarea sin claves; secreto de la base inyectado por el servicio.
resource "aws_cloudwatch_log_group" "aplicacion" {
  name              = "/pedidos"
  retention_in_days = 14
  tags              = local.etiquetas
}
resource "aws_iam_role" "ejecucion" {
  name = "pedidos-ejecucion"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "sts:AssumeRole", Principal = { Service = "ecs-tasks.amazonaws.com" } }]
  })
}
resource "aws_iam_role_policy_attachment" "ejecucion" {
  role       = aws_iam_role.ejecucion.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}
resource "aws_iam_role_policy" "leer_secreto_de_la_base" {
  name = "leer-secreto-de-la-base"
  role = aws_iam_role.ejecucion.id
  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = ["secretsmanager:GetSecretValue"], Resource = aws_db_instance.base.master_user_secret[0].secret_arn }]
  })
}
resource "aws_ecs_cluster" "principal" {
  name = "pedidos"
  tags = local.etiquetas
}
resource "aws_ecs_task_definition" "aplicacion" {
  family                   = "pedidos"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.ejecucion.arn
  task_role_arn            = var.rol_tarea
  container_definitions = jsonencode([{
    name         = "pedidos"
    image        = var.imagen
    essential    = true
    portMappings = [{ containerPort = 8080 }]
    environment = [
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.base.address}:5432/tienda" },
      { name = "SPRING_DATASOURCE_USERNAME", value = "pedidos" },
    ]
    secrets = [{ name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${aws_db_instance.base.master_user_secret[0].secret_arn}:password::" }]
    logConfiguration = {
      logDriver = "awslogs"
      options   = { "awslogs-group" = aws_cloudwatch_log_group.aplicacion.name, "awslogs-region" = "us-east-1", "awslogs-stream-prefix" = "pedidos" }
    }
  }])
  tags = local.etiquetas
}
resource "aws_ecs_service" "aplicacion" {
  name            = "pedidos"
  cluster         = aws_ecs_cluster.principal.id
  task_definition = aws_ecs_task_definition.aplicacion.arn
  desired_count   = 2
  launch_type     = "FARGATE"
  network_configuration {
    subnets         = var.subredes_privadas
    security_groups = [var.sg_aplicacion]
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.aplicacion.arn
    container_name   = "pedidos"
    container_port   = 8080
  }
  tags = local.etiquetas
}

# --- Punto 1: presupuesto mensual con avisos al 50 %, 80 % y 100 % (real) y 100 % (pronóstico).
resource "aws_budgets_budget" "mensual" {
  name         = "pedidos-mensual"
  budget_type  = "COST"
  limit_amount = "150"
  limit_unit   = "USD"
  time_unit    = "MONTHLY"
  dynamic "notification" {
    for_each = [50, 80, 100]
    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "PERCENTAGE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = ["equipo@example.com"]
    }
  }
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = ["equipo@example.com"]
  }
}
