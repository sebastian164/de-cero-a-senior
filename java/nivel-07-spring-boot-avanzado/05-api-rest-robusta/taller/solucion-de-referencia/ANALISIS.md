# Taller 07-05 — API REST de citas médicas (solución)

Spring Boot 4.1.1 (Spring Framework 7, Jackson 3), Spring Data JPA con H2 en memoria, springdoc-openapi 3.2.0. `mvn test` ejecuta 9 pruebas con MockMvc.

1. **Diseño antes de programar**: `DISENO.md` (recursos, rutas, códigos de éxito y de error). Acción no CRUD: `POST /v1/citas/{id}/cancelacion` (204; 409 si ya estaba cancelada; 422 si faltan menos de 24 h; 404 si no existe).
2. **Validación y errores uniformes**: el DTO `CrearCitaRequest` solo valida formato (`@NotNull`, `@Positive`, `@NotBlank`, `@Size`). Reglas de negocio (cita en el pasado, cancelación tardía, horario ocupado) en el servicio y la entidad. `ManejadorDeErrores` extiende `ResponseEntityExceptionHandler` y devuelve `application/problem+json` con `type`, `title`, `status`, `detail`, `instance` y, en validación, un mapa `errores` por campo. Probado: 400 de validación, 400 sin `Idempotency-Key`, 400 con un enum inválido en el filtro, 404, 409 y 422; nunca sale la traza.
3. **Paginación, filtro y orden**: `GET /v1/citas?medicoId=&estado=&desde=&page=&size=&sort=` con `Specification` y `@PageableDefault(size = 20, sort = "inicio")`; tamaño máximo 100. Con 25 citas: 20 en la primera página, 2 páginas; filtro por médico + `sort=inicio,desc` devuelve `p23, p21, p19`.
4. **Idempotencia**: tabla `ClaveIdempotencia` (clave primaria = la clave, huella del cuerpo y la cita creada). El reintento con la misma clave y el mismo cuerpo devuelve la misma cita (mismo `Location`, cabecera `Idempotent-Replayed: true`) y no crea otra; la misma clave con otro cuerpo responde 422.
5. **OpenAPI**: `/v3/api-docs` publica las rutas de v1 y v2, con `Idempotency-Key` como cabecera obligatoria; `/swagger-ui/index.html` responde 200.
6. **Cambio incompatible con versionado en la ruta**: v2 cambia `pacienteNombre` (texto) por `paciente {id, nombre}` y `fecha` + `hora` por `inicio` con zona horaria. `/v2/citas` convive con `/v1/citas`, que conserva su forma; ambas usan el mismo servicio y solo cambia la representación. Probado que v1 sigue respondiendo como antes.

Hallazgo de versión: devolver `Page<T>` directamente se serializa como `PagedModel` (`content` + `page {size, number, totalElements, totalPages}`) al activar `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`; ver el proyecto de verificación del nivel 7 para lo que pasa sin él.
