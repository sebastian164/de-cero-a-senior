# Proveedor apuntando a LocalStack (puerto 4566). Las claves "test" son las ficticias de LocalStack.
terraform {
  required_providers {
    aws = { source = "hashicorp/aws", version = "6.21.0" }
  }
}
provider "aws" {
  region                      = "us-east-1"
  access_key                  = "test"
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true
  endpoints {
    rds            = "http://localhost:4566"
    secretsmanager = "http://localhost:4566"
    sts            = "http://localhost:4566"
  }
}
variable "ambiente" { type = string }
resource "aws_secretsmanager_secret" "pedidos_db" { name = "pedidos-db-${var.ambiente}" }
resource "aws_secretsmanager_secret_version" "pedidos_db" {
  secret_id     = aws_secretsmanager_secret.pedidos_db.id
  secret_string = "clave-de-prueba-${var.ambiente}"
}
data "aws_secretsmanager_secret_version" "pedidos_db" {
  secret_id  = aws_secretsmanager_secret.pedidos_db.id
  depends_on = [aws_secretsmanager_secret_version.pedidos_db]
}
