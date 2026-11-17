# Proyecto 4: API de reservas con arquitectura hexagonal, solución de referencia

> **Intenta el proyecto antes de leer esto.** Ver la solución primero convierte el proyecto en lectura y no en práctica. Es *una* solución mínima y verificada, no *la* solución: la revisión de tu entrega mira tu diseño, tus decisiones y tu defensa.

Enunciado: [sección 4 de proyectos/README.md](../../README.md#4-api-de-reservas-con-arquitectura-hexagonal). Spring Boot 4.1.1, PostgreSQL 18.7, Flyway, Testcontainers.

## Qué requisitos cubre y dónde

| Requisito del enunciado | Dónde está | Prueba que lo demuestra |
|---|---|---|
| Disponibilidad por franjas | `GET /reservas/disponibilidad`; `Franja.huecosLibres` | `disponibilidad_por_franjas_muestra_lo_ocupado_y_los_huecos_libres`, `los_huecos_libres_son_lo_que_no_cubre_ninguna_franja_ocupada` |
| Reservas y cancelaciones con política de plazos | `Reserva.cancelar` (hasta 24 horas antes del inicio) | `se_cancela_hasta_24_horas_antes_del_inicio_y_despues_no`, `cancelar_respeta_el_plazo_y_pasa_la_franja_a_la_lista_de_espera` |
| Lista de espera | `ServicioReservas.cancelar` entrega la franja al primero de la lista; `POST /reservas/espera` | `al_cancelar_la_franja_pasa_al_primero_de_la_lista_de_espera` |
| Nunca dos reservas en la misma franja, tampoco con peticiones simultáneas | restricción `EXCLUDE` de PostgreSQL en `db/migration/V1__reservas.sql` | `con_veinte_peticiones_simultaneas_solo_una_reserva_gana` (en dos pruebas, contra PostgreSQL real) |
| API REST con validación y errores uniformes | `ControladorReservas`, `ErroresUniformes` (`application/problem+json`, RFC 9457) | `http_crear_idempotencia_errores_uniformes_y_openapi` |
| Paginación | parámetros `pagina` y `tamano` de `GET /reservas` | `cada_usuario_ve_lo_suyo_el_admin_todo_y_la_lista_se_pagina` |
| Idempotencia | cabecera `Idempotency-Key`; tabla `claves_idempotencia` | `repetir_la_misma_clave_devuelve_la_misma_reserva_y_no_crea_otra` |
| Documentación OpenAPI | springdoc: `/v3/api-docs` | `http_crear_idempotencia_errores_uniformes_y_openapi` |
| Seguridad: cada usuario ve y modifica lo suyo, el administrador todo | `Seguridad.java` (servidor de recursos OAuth2 con JWT); `ServicioReservas.ver` | `seguridad_solo_entra_quien_trae_un_token_firmado_y_vigente...`, `cada_usuario_ve_y_cancela_solo_lo_suyo_y_el_admin_todo` |
| El dominio no depende de Spring ni de JPA, verificado con una prueba de arquitectura | paquetes `dominio`, `aplicacion`, `infraestructura` | `ArquitecturaTest` (ArchUnit) |
| Reglas probadas sin infraestructura | `ServicioReservas` con un repositorio en memoria y un reloj fijo | `ServicioReservasSinInfraestructuraTest`, `FranjaYReservaTest` |
| Pruebas de integración con base real | Testcontainers: `PostgresDePrueba` | `ReservasTest`, `ReservasConTestcontainersTest` |
| Tres ADR | [docs/adr/](docs/adr/) | no aplica |

**Qué no cubre:** la extensión (un segundo adaptador de entrada, mensajería o línea de comandos, que reutilice los casos de uso) y la validación de tokens contra un proveedor de identidad real: las pruebas usan una clave RSA generada al vuelo.

## Cómo probarla

Requiere JDK 21, Maven y Docker en marcha (Testcontainers levanta su propio PostgreSQL; **no necesitas `docker compose` para probar**).

```bash
mvn clean verify
```

Ejecuta 23 pruebas: el dominio y los casos de uso sin infraestructura, la arquitectura, y la API completa contra un PostgreSQL real, incluidas las 20 peticiones simultáneas.

## Cómo levantarla y usarla

1. Levanta PostgreSQL (puerto 5432, base `reservas`, usuario `reservas`):

```bash
docker compose up -d --wait
```

2. Genera las claves de desarrollo y un token (la API valida la firma de los tokens y solo conoce la clave pública; `dev/Token.java` no necesita nada más que el JDK):

```bash
java dev/Token.java ana
```

Imprime un JWT de 1 hora para el usuario `ana` y crea `dev/jwt-publica.pem` y `dev/jwt-privada.pem` (claves de juguete, ignoradas por git). Para un administrador: `java dev/Token.java admin ADMIN`.

3. Arranca la API (puerto 8080):

```bash
mvn spring-boot:run
```

4. Úsala:

```bash
TOKEN=$(java dev/Token.java ana)

# crear una reserva (con clave de idempotencia: repetir la petición devuelve la misma reserva)
curl -i -X POST localhost:8080/reservas -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: abc-1' -d '{"espacio":"sala-1","inicio":"2031-05-01T10:00:00Z","fin":"2031-05-01T11:00:00Z"}'

# la misma franja otra vez: 409 con application/problem+json
# mis reservas, paginadas
curl "localhost:8080/reservas?pagina=0&tamano=10" -H "Authorization: Bearer $TOKEN"

# huecos libres de una sala en una ventana
curl "localhost:8080/reservas/disponibilidad?espacio=sala-1&desde=2031-05-01T08:00:00Z&hasta=2031-05-01T18:00:00Z" -H "Authorization: Bearer $TOKEN"

# sin token: 401
curl -i localhost:8080/reservas

# documentación OpenAPI
curl localhost:8080/v3/api-docs
```

Cancelar: `DELETE /reservas/{id}` (204, o 422 si faltan menos de 24 horas). Ponerse en la lista de espera de una franja ocupada: `POST /reservas/espera` con el mismo cuerpo que una reserva.

5. Al terminar: `docker compose down -v`.

**Variables:** `RESERVAS_DB_URL`, `RESERVAS_DB_USUARIO`, `RESERVAS_DB_CLAVE`, `PUERTO` (8080) y `JWT_CLAVE_PUBLICA` (por defecto `file:dev/jwt-publica.pem`; en producción usarías `issuer-uri` de tu proveedor de identidad). Si tu 5432 está ocupado: `PG_PUERTO=5433 docker compose up -d --wait` y `RESERVAS_DB_URL=jdbc:postgresql://localhost:5433/reservas`.

## Decisiones de diseño

Tres ADR en [docs/adr/](docs/adr/):

- [0001: el dominio y los casos de uso no conocen Spring ni la base](docs/adr/0001-hexagonal-con-prueba-de-arquitectura.md)
- [0002: la garantía contra la doble reserva vive en la base de datos](docs/adr/0002-doble-reserva-con-restriccion-de-exclusion.md)
- [0003: la API es un servidor de recursos que valida JWT](docs/adr/0003-seguridad-con-jwt-de-servidor-de-recursos.md)
