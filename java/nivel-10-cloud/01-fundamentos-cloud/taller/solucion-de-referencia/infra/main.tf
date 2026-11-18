terraform {
  required_providers {
    aws = { source = "hashicorp/aws", version = "6.21.0" }
  }
}
variable "endpoint" {
  type    = string
  default = "http://localhost:4566" # LocalStack; en AWS real se quita el bloque endpoints
}

provider "aws" {
  region                      = "us-east-1"
  access_key                  = "test"
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true
  s3_use_path_style           = true
  endpoints {
    ec2 = var.endpoint
    s3  = var.endpoint
    iam = var.endpoint
    sts = var.endpoint
  }
}

locals { zonas = ["us-east-1a", "us-east-1b"] }

# Red con dos zonas de disponibilidad: una subred pública (balanceador) y una privada (aplicación y base) por zona.
resource "aws_vpc" "principal" {
  cidr_block = "10.0.0.0/16"
  tags       = { Name = "pedidos", app = "pedidos" }
}
resource "aws_subnet" "publica" {
  count             = 2
  vpc_id            = aws_vpc.principal.id
  cidr_block        = "10.0.${count.index}.0/24"
  availability_zone = local.zonas[count.index]
  tags              = { Name = "publica-${local.zonas[count.index]}", app = "pedidos" }
}
resource "aws_subnet" "privada" {
  count             = 2
  vpc_id            = aws_vpc.principal.id
  cidr_block        = "10.0.${count.index + 10}.0/24"
  availability_zone = local.zonas[count.index]
  tags              = { Name = "privada-${local.zonas[count.index]}", app = "pedidos" }
}
resource "aws_security_group" "aplicacion" {
  name   = "pedidos-aplicacion"
  vpc_id = aws_vpc.principal.id
  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [for s in aws_subnet.publica : s.cidr_block] # solo el balanceador entra
  }
}
resource "aws_security_group" "base" {
  name   = "pedidos-base"
  vpc_id = aws_vpc.principal.id
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.aplicacion.id] # solo la aplicación llega a la base
  }
}

# Archivos en almacenamiento de objetos, privados, con acceso por rol y sin claves.
resource "aws_s3_bucket" "archivos" { bucket = "pedidos-fundamentos-archivos" }
resource "aws_s3_bucket_public_access_block" "archivos" {
  bucket                  = aws_s3_bucket.archivos.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
data "aws_iam_policy_document" "confianza" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::000000000000:root"]
    }
  }
}
data "aws_iam_policy_document" "archivos" {
  statement {
    actions   = ["s3:GetObject", "s3:PutObject"]
    resources = ["${aws_s3_bucket.archivos.arn}/*"]
  }
}
resource "aws_iam_role" "aplicacion" {
  name               = "pedidos-aplicacion"
  assume_role_policy = data.aws_iam_policy_document.confianza.json
}
resource "aws_iam_role_policy" "archivos" {
  role   = aws_iam_role.aplicacion.id
  name   = "archivos"
  policy = data.aws_iam_policy_document.archivos.json
}
output "vpc_id" { value = aws_vpc.principal.id }
output "subredes_publicas_ids" { value = aws_subnet.publica[*].id }
output "subredes_privadas_ids" { value = aws_subnet.privada[*].id }
output "sg_aplicacion" { value = aws_security_group.aplicacion.id }
output "sg_base" { value = aws_security_group.base.id }
output "subredes_privadas" { value = aws_subnet.privada[*].availability_zone }
output "rol" { value = aws_iam_role.aplicacion.arn }
