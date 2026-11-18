terraform {
  # Estado remoto con bloqueo: en un bucket S3 (aquí LocalStack; en AWS real, un bucket cifrado con versionado). La
  # conexión se pasa con -backend-config=backend.hcl porque un bloque backend no admite variables.
  backend "s3" {}
  required_providers {
    aws = { source = "hashicorp/aws", version = "6.21.0" }
  }
}

variable "ambiente" { type = string }
variable "endpoint" {
  type    = string
  default = "http://localhost:4566" # LocalStack; en AWS real se quita el bloque endpoints
}
variable "versionado" { type = bool }
variable "clave_bd" {
  type      = string
  sensitive = true
}

provider "aws" {
  region                      = "us-east-1"
  access_key                  = "test" # ficticia, solo para LocalStack; en AWS real no hay claves: se usa un rol
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true
  s3_use_path_style           = true
  endpoints {
    s3             = var.endpoint
    iam            = var.endpoint
    sts            = var.endpoint
    secretsmanager = var.endpoint
  }
}

locals {
  nombre    = "pedidos-${var.ambiente}"
  etiquetas = { app = "pedidos", ambiente = var.ambiente, gestionado-por = "opentofu" }
}

# Archivos de la aplicación: almacenamiento de objetos, privado.
resource "aws_s3_bucket" "archivos" {
  bucket        = "${local.nombre}-archivos"
  force_destroy = var.ambiente != "produccion" # los ambientes desechables se destruyen aunque tengan objetos; producción nunca
  tags          = local.etiquetas
  lifecycle {
    precondition {
      condition     = terraform.workspace == var.ambiente
      error_message = "El workspace (${terraform.workspace}) no coincide con el ambiente (${var.ambiente}): se estaba aplicando el tfvars equivocado."
    }
  }
}
resource "aws_s3_bucket_versioning" "archivos" {
  bucket = aws_s3_bucket.archivos.id
  versioning_configuration { status = var.versionado ? "Enabled" : "Suspended" }
}
resource "aws_s3_bucket_public_access_block" "archivos" {
  bucket                  = aws_s3_bucket.archivos.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Identidad de la aplicación: un rol con permisos solo sobre su bucket (mínimo privilegio), sin claves.
data "aws_iam_policy_document" "confianza" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}
data "aws_iam_policy_document" "archivos" {
  statement {
    actions   = ["s3:GetObject", "s3:PutObject"]
    resources = ["${aws_s3_bucket.archivos.arn}/*"]
  }
  statement {
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.archivos.arn]
  }
}
resource "aws_iam_role" "aplicacion" {
  name               = "${local.nombre}-aplicacion"
  assume_role_policy = data.aws_iam_policy_document.confianza.json
  tags               = local.etiquetas
}
resource "aws_iam_role_policy" "archivos" {
  name   = "archivos"
  role   = aws_iam_role.aplicacion.id
  policy = data.aws_iam_policy_document.archivos.json
}

# Secreto de la base de datos: nunca en el repositorio; llega por variable de entorno TF_VAR_clave_bd.
resource "aws_secretsmanager_secret" "bd" {
  name                    = "${local.nombre}-bd"
  recovery_window_in_days = 0
  tags                    = local.etiquetas
}
resource "aws_secretsmanager_secret_version" "bd" {
  secret_id     = aws_secretsmanager_secret.bd.id
  secret_string = var.clave_bd
}

output "bucket" { value = aws_s3_bucket.archivos.bucket }
output "rol" { value = aws_iam_role.aplicacion.arn }
output "secreto" { value = aws_secretsmanager_secret.bd.name }
