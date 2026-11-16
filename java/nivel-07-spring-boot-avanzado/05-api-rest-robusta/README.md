# 05 — Diseño de una API REST robusta

## Idea

Una API es un contrato con personas que no conoces y código que no controlas. Lo que publiques hoy lo tendrás que sostener durante años. Diseñarla bien es cuidar ese contrato.

## Lo esencial

### Recursos y verbos

| Verbo | Uso | Idempotente | Seguro |
|---|---|---|---|
| `GET` | Leer | Sí | Sí |
| `POST` | Crear o ejecutar una acción | No | No |
| `PUT` | Reemplazar completo | Sí | No |
| `PATCH` | Modificar parcialmente | No necesariamente | No |
| `DELETE` | Eliminar | Sí | No |

Recursos en plural y con sustantivos: `/pedidos/{id}/lineas`. Las acciones que no encajan en CRUD se modelan como subrecursos: `POST /pedidos/{id}/cancelacion`.

### Códigos de estado que importan

`200` correcto · `201` creado (con cabecera `Location`) · `204` sin contenido · `400` petición mal formada · `401` no autenticado · `403` sin permiso · `404` no existe · `409` conflicto de estado · `422` regla de negocio incumplida · `429` demasiadas peticiones · `500` error del servidor · `503` no disponible.

### Elementos de una API profesional

- **DTOs de entrada y salida**, nunca entidades.
- **Validación** con Bean Validation (`@Valid`, `@NotBlank`, `@Positive`) para el formato; las reglas de negocio, en el dominio.
- **Errores uniformes** con *Problem Details* (RFC 9457) y un `@RestControllerAdvice`.
- **Paginación, filtros y orden** en toda colección.
- **Idempotencia** en operaciones que crean o cobran: cabecera `Idempotency-Key`.
- **Versionado:** en la ruta (`/v1/`) o por cabecera. Lo esencial: no romper a los clientes; agregar campos es compatible, quitar o renombrar no.
- **Concurrencia optimista:** `ETag` e `If-Match`.
- **Documentación** generada: OpenAPI (springdoc).
- **Límites de tasa y tamaño.**

## Ejemplo

```java
record CrearPedidoRequest(
        @NotNull Long clienteId,
        @NotEmpty @Valid List<LineaRequest> lineas) { }

record LineaRequest(@NotNull Long productoId, @Positive int cantidad) { }

@RestController
@RequestMapping("/v1/pedidos")
class PedidoController {

    @PostMapping
    ResponseEntity<PedidoResponse> crear(
            @RequestHeader("Idempotency-Key") String clave,
            @Valid @RequestBody CrearPedidoRequest request) {
        PedidoResponse creado = crearPedido.ejecutar(clave, request);
        return ResponseEntity
                .created(URI.create("/v1/pedidos/" + creado.id()))
                .body(creado);
    }

    @GetMapping
    Page<PedidoResumen> listar(@RequestParam(required = false) EstadoPedido estado,
                               @PageableDefault(size = 20, sort = "fecha") Pageable pagina) {
        return consulta.listar(estado, pagina);
    }
}
```

Respuesta de error:

```json
{
  "type": "https://api.tienda.example/errores/fondos-insuficientes",
  "title": "Fondos insuficientes",
  "status": 422,
  "detail": "El saldo no alcanza para el pedido",
  "instance": "/v1/pedidos",
  "traceId": "8f2c1a"
}
```

## Errores comunes

- Devolver `200` con `{"error": "..."}` en el cuerpo.
- Verbos en las rutas: `/obtenerPedidos`, `/crearPedido`.
- Exponer entidades JPA, identificadores internos o trazas de error.
- Colecciones sin paginar.
- Cambios incompatibles sin versión nueva.
- `POST` de creación sin idempotencia: un reintento duplica el pedido.
- Validar reglas de negocio con anotaciones en el DTO.
- Mensajes de error que revelan estructura interna.

## Taller

Diseña e implementa la API de un sistema de reservas de citas médicas.

1. Define los recursos, las rutas y los códigos de cada operación antes de programar. Incluye al menos una acción que no sea CRUD.
2. Implementa validación de formato y manejo uniforme de errores con Problem Details.
3. Agrega paginación, filtro y orden al listado.
4. Implementa idempotencia en la creación de citas.
5. Publica la documentación OpenAPI.
6. Introduce un cambio incompatible y resuélvelo con versionado, manteniendo la versión anterior funcionando.

Rama `taller/n7-05-api-rest`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué es la idempotencia y por qué importa en una API de pagos?
2. `401` frente a `403`; `400` frente a `422`; `404` frente a `409`.
3. Estrategias de versionado: ventajas y costos de cada una.
4. ¿Cómo evolucionas una API pública sin romper a los clientes?
5. Paginación por desplazamiento frente a paginación por cursor.
6. ¿Cuándo REST no es la mejor opción (gRPC, GraphQL, eventos)?
