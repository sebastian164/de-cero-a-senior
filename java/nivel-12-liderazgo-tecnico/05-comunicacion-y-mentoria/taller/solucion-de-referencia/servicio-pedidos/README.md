# pedidos

Servicio de pedidos (Spring Boot 4, Java 21).

## Arrancar en local (5 minutos)
1. Requisitos: JDK 21, Docker.
2. `docker compose up --build`  (aplicación en :8080, PostgreSQL con comprobación de salud).
3. Espera a que el registro de `app` muestre `Started` (unos segundos) y prueba: `curl -XPOST localhost:8080/api/pedidos -H 'Content-Type: application/json' -d '{"clienteId":1,"total":10}'`.

## Pruebas
`./mvnw verify`

## Configuración
Todo por variables de entorno: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`. Sin secretos en el repositorio.

## Si algo falla
- "no password was provided" o "password authentication failed": falta `SPRING_DATASOURCE_PASSWORD` o no coincide con la de la base.
- Puerto 8080 ocupado: cambiar el mapeo en `compose.yaml`.
