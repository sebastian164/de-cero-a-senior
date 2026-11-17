# Nivel 10 — Cloud

Requisito: Nivel 9. Cómo se empaqueta, se despliega y se opera una aplicación en infraestructura moderna.

| # | Sesión |
|---|---|
| 01 | [Fundamentos de la nube](01-fundamentos-cloud/) |
| 02 | [Contenedores y Docker](02-contenedores-y-docker/) |
| 03 | [Kubernetes esencial para desarrolladores](03-kubernetes-esencial/) |
| 04 | [Integración y entrega continuas](04-ci-cd/) |
| 05 | [Aplicaciones de doce factores e infraestructura como código](05-doce-factores-e-iac/) |

Los archivos de configuración de estas sesiones se ejecutaron en local (Docker Desktop, kind v0.33.0, GitHub Actions con `act` 0.2.97, OpenTofu 1.13.9 contra LocalStack 4.14.0). Lo que no existe en LocalStack Community (RDS, ECS, ELB, IAM aplicado) solo se validó o se planificó. Los servicios en la nube reales cuestan dinero: activa alertas de presupuesto antes de crear cualquier recurso y elimina lo que no uses.
