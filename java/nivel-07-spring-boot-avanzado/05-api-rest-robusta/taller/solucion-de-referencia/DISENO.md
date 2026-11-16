# API de citas médicas — diseño previo (paso 1)

Recurso principal: **citas**. Los médicos y pacientes se referencian por identificador.

| Operación | Ruta | Éxito | Errores |
|---|---|---|---|
| Crear cita (idempotente con `Idempotency-Key`) | `POST /v1/citas` | `201` + `Location` | `400` formato, `409` horario del médico ocupado, `422` cita en el pasado o clave reutilizada con otro cuerpo, `400` sin `Idempotency-Key` |
| Consultar una cita | `GET /v1/citas/{id}` | `200` | `404` |
| Listar (paginado, filtro por médico, estado y fecha; orden) | `GET /v1/citas?medicoId=&estado=&desde=&page=&size=&sort=` | `200` | `400` parámetro inválido |
| **Cancelar** (acción no CRUD, como subrecurso) | `POST /v1/citas/{id}/cancelacion` | `204` | `404`, `409` ya cancelada, `422` faltan menos de 24 h |
| Documentación | `GET /v3/api-docs`, `/swagger-ui.html` | `200` | |

Versión 2 (cambio incompatible): `GET /v2/citas/{id}` y `GET /v2/citas` devuelven `paciente` como objeto `{id, nombre}` en lugar del texto `pacienteNombre`, y `inicio` como un solo instante. `/v1` sigue respondiendo con la forma anterior.

Errores: siempre `application/problem+json` (RFC 9457) con `type`, `title`, `status`, `detail`, `instance` y, en validación, `errores` por campo.
