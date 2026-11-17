# Infraestructura como código de la tienda: OpenTofu aplica los manifiestos de ../k8s/tienda.yaml sobre el clúster.
# El secreto NO está aquí ni en el repositorio: se crea aparte (kubectl create secret ...) y el Deployment solo lo referencia.
terraform {
  required_version = ">= 1.8"
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.38"
    }
  }
}

variable "kubeconfig" {
  type        = string
  description = "Ruta del kubeconfig del clúster de destino"
}

variable "manifiesto" {
  type        = string
  default     = ""
  description = "Manifiestos a aplicar; por defecto ../k8s/tienda.yaml"
}

provider "kubernetes" {
  config_path = var.kubeconfig
}

locals {
  documentos = [for d in split("\n---\n", file(var.manifiesto != "" ? var.manifiesto : "${path.module}/../k8s/tienda.yaml")) : yamldecode(d) if length(regexall("(?m)^kind:", d)) > 0]
}

resource "kubernetes_manifest" "tienda" {
  for_each = { for d in local.documentos : "${lower(d.kind)}-${d.metadata.name}" => d }
  manifest = each.value
}

output "recursos" {
  value = keys(kubernetes_manifest.tienda)
}
