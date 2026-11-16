# Taller 05-03 — MVC (solución)

`mvn test` ejecuta 8 pruebas.

## 1. Reescritura del controlador gordo

| Pieza | Clase |
|---|---|
| Controlador delgado (4 líneas de cuerpo) | `web/PedidoController`: `POST /pedidos/{id}/cancelar` → `204` |
| Traducción de errores a HTTP | `web/ManejadorDeErrores` (`@RestControllerAdvice`): regla incumplida → `409` con Problem Details; inexistente → `404` |
| Caso de uso | `aplicacion/CancelarPedido`: carga, pide al pedido que se cancele, guarda, notifica |
| Las dos reglas | `dominio/Pedido.cancelar(ahora)`: "Ya fue enviado" y "Pasaron más de 24 horas" |

El reloj se inyecta (`Clock`), así la regla de las 24 horas se prueba en el límite exacto sin esperar.

## 2. Pruebas

- `ReglasDeCancelacionTest`: sin Spring. Cubre dentro del plazo (24 h exactas se permiten), enviado y plazo vencido por un segundo.
- `PedidoControllerTest` (`@WebMvcTest`): solo la capa web con el caso de uso simulado. Verifica 204, 409, 404 y 400 (id no numérico).

## 3. Segundo canal

`mensajeria/ConsumidorCancelaciones` recibe el id como texto. `DosCanalesTest` cancela un pedido por HTTP y dos por mensaje (uno rechazado por estar enviado).

Compartieron: `CancelarPedido`, `Pedido` (las reglas), los puertos `RepositorioPedidos` y `Notificador` y sus implementaciones. Solo difiere la traducción: el controlador convierte el rechazo en `409`; el consumidor lo registra como mensaje no procesable en lugar de reintentarlo.

## 4. Clasificación del controlador más largo (el controlador gordo del README)

| Bloque | Tipo |
|---|---|
| `@PostMapping`, `@PathVariable Long id` | Entrada |
| `repositorio.findById(id).orElseThrow()` | Persistencia |
| `if (...equals("ENVIADO")) return badRequest(...)` | Negocio (y además salida: usa `400` para una regla, debería ser `409`) |
| `if (ChronoUnit.HOURS.between(...) > 24)` | Negocio (y compara horas truncadas: 24 h 59 min pasa la regla) |
| `pedido.setEstado("CANCELADO")` | Negocio |
| `repositorio.save(pedido)` | Persistencia |
| `correo.enviar(...)` | Infraestructura (efecto externo) |
| `return ResponseEntity.ok().build()` | Salida |

Hallazgo del ejercicio: `ChronoUnit.HOURS.between` trunca, así que el original deja cancelar hasta 24 h 59 min 59 s. La versión del dominio compara `Duration` y rechaza desde 24 h y 1 s; la prueba `noCancelaPasadas24Horas` lo fija.

## 5. MVC, MVP y MVVM

| Patrón | Quién actualiza la vista | Relación vista-lógica |
|---|---|---|
| MVC | El controlador elige la vista; en el clásico, la vista observa al modelo | La vista conoce al modelo |
| MVP | El presentador le dice a una vista pasiva qué mostrar mediante una interfaz | La vista no conoce al modelo; se prueba el presentador con una vista falsa |
| MVVM | Enlace de datos (*data binding*) entre la vista y el modelo de vista | El modelo de vista no conoce a la vista |

Angular usa MVVM: la plantilla se enlaza a las propiedades y señales del componente (el modelo de vista) con `{{ }}`, `[prop]` y `(evento)`; los servicios hacen de modelo.
