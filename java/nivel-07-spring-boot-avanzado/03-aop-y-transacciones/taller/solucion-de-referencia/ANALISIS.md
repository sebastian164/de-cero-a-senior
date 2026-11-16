# Taller 07-03 — AOP, proxies y `@Transactional` (solución)

Spring Boot 4.1.1 + `spring-boot-starter-aspectj`, JDBC contra **PostgreSQL 17** (levantado con el `docker-compose.yml` de esta carpeta, puerto 5432 por defecto). `mvn test` ejecuta 9 pruebas y cuenta filas reales en la base.

Lote usado: `ana` (10), `luis` (−1, inválido), `eva` (5). Cada pedido son dos filas (cabecera + detalle) y la validación falla **después** de insertar el detalle, así que se ve si hubo reversión.

## 1. Autoinvocación

`ServicioPedidos.importar` (el ejemplo del README) llama a `this.guardar(dto)`, anotado con `@Transactional`. Resultado: **4 filas** quedan en la base: las 2 de `ana` y las 2 de `luis`, incluida la inválida. No hubo transacción: cada `INSERT` se confirmó solo.

| Corrección | Filas | Prueba |
|---|---|---|
| Mover `guardar` a otro bean (`GuardadoTransaccional`) | 2 (solo `ana`; `luis` se revierte entero) | `correccionConOtroBean` |
| `TransactionTemplate` explícito | 2 | `correccionConTransactionTemplate` |

Verificado además: un método `@Transactional` **de paquete** llamado a través del proxy sí abre transacción (0 filas tras el fallo). Desde Spring Framework 6.0 los proxies de clase (CGLIB) aplican `@Transactional` a métodos no privados; la regla "solo métodos públicos" del README era de versiones anteriores y se corrigió.

## 2. Excepción comprobada

`conDefecto` inserta y lanza `SaldoInsuficiente extends Exception`: la fila **queda guardada**. Con `@Transactional(rollbackFor = Exception.class)` se revierte.

## 3. `UnexpectedRollbackException`

`Reversion.externoQueCaptura()` (transaccional) llama a `Interno.fallar()` (transaccional, `REQUIRED`), que lanza una `RuntimeException`. El externo la captura y sigue. Al confirmar: *"Transaction silently rolled back because it has been marked as rollback-only"*. Causa: con `REQUIRED` hay **una sola** transacción física; el interceptor del método interno la marcó para revertir al ver salir la excepción, y capturarla afuera no la desmarca. Tampoco queda la fila del externo.

## 4. Aspecto de auditoría

`@Auditable` + `AspectoDeAuditoria` (`@Around("@annotation(auditable)")`, que enlaza la anotación como parámetro). Registra quién (desde `UsuarioActual`; con Spring Security sería el `SecurityContext`), qué y si terminó bien o falló. Probado: dos llamadas auditadas (una exitosa y una fallida) y una no anotada que no deja registro.

## 5. `servicio.getClass()`

`transacciones.ServicioPedidos$$SpringCGLIB$$0`: una **subclase generada en ejecución** por CGLIB (empaquetado dentro de Spring). Su superclase es `ServicioPedidos`; sobrescribe los métodos para pasar por el interceptor de transacciones antes de delegar en el objeto real. Es CGLIB y no un proxy dinámico del JDK porque Spring Boot usa proxies de clase por defecto (`spring.aop.proxy-target-class=true`).

## 6. `REQUIRED` frente a `REQUIRES_NEW` para auditar

`operarConAuditoria` registra el intento y luego falla:

| Propagación del registro de auditoría | ¿Sobrevive al fallo de la operación? |
|---|---|
| `REQUIRED` | No: es parte de la misma transacción y se revierte con ella |
| `REQUIRES_NEW` | Sí: se confirma en su propia transacción antes de que la externa falle |

Costo de `REQUIRES_NEW`: suspende la transacción externa y toma **otra conexión** del pool mientras tanto; con muchos hilos haciendo lo mismo puede agotar el pool (cada hilo retiene dos conexiones).
