# Taller 07 — Los pedidos en PostgreSQL con JPA

Proyecto Maven en esta carpeta: Spring Boot 4.1.1 (Spring Framework 7.0.9, Hibernate 7.4.5.Final, HikariCP 7.0.2,
driver PostgreSQL 42.7.13), JDK 21.0.13, Maven 3.10.1, PostgreSQL 17.12. `mvn package` compila y ejecuta 6 pruebas JUnit con
Testcontainers 2.0.5 (PostgreSQL 17 en un puerto libre); además el jar arranca contra el PostgreSQL del
`compose.yaml` de esta carpeta (puerto 5432) en el puerto 8080 y atiende peticiones con `curl`.

## Paso 1. Compila el núcleo sin copiarlo

`pom.xml` añade `../../../06-adaptadores-y-composicion/taller/solucion-de-referencia/src` con `build-helper-maven-plugin` y excluye
`tienda/adaptadores/**` y `tienda/Main.java`, igual que `hexagonal/proyecto-jpa`. Las pruebas reutilizan, también sin copiar,
los dobles `PedidosQueFallan` e `InventarioConInterferencia` de las sesiones 04 y 05.

## Paso 2. Esquema a mano

`src/main/resources/schema.sql`: `producto`, `existencias` (con `version`), `pedido` (con `version`), `linea_de_pedido`,
`peticion_procesada` y `evento_pendiente`, más los datos de la tienda del taller 01.

| Restricción | ¿Repite una regla del dominio? | Por qué se pone |
|---|---|---|
| `existencias.unidades >= 0` | Sí ("no se vende más de lo que hay") | Última defensa si alguien escribe sin pasar por el dominio |
| `linea_de_pedido.cantidad >= 1` | Sí (cantidad mínima) | Igual |
| `pedido.cliente <> ''` | Sí (cliente obligatorio) | Igual |
| `peticion_procesada.clave` clave primaria | No: es el mecanismo de idempotencia | Dos transacciones con la misma clave no pueden confirmar las dos |
| Claves foráneas (`existencias → producto`, `linea → pedido`, `peticion → pedido`) | No | Integridad referencial |
| `unique (pedido_id, posicion)` | No | El orden de las líneas es parte del pedido |
| El tope del pedido | **No se repite** | Es una regla de negocio que cambia; en la base sería una segunda fuente de verdad |

## Paso 3. Un modelo o dos

Dos: `PedidoJpa`, `LineaDePedidoJpa`, `ExistenciasJpa`, `ProductoJpa`, `EventoPendienteJpa`. El dominio no cambió nada
(`Pedido` sigue siendo `final`, sin constructor vacío y sin `version`). Lo que cuesta: cinco clases y la traducción en los
dos sentidos (`nuevo`/`copiarDesde`/`aDominio`), y que la versión viva en la fila y no en el objeto, lo que obliga a que
el caso de uso lea y escriba **en la misma transacción** (lo hace siempre).

## Paso 4. Entidades y la relación pedido-líneas

`@OneToMany(mappedBy = "pedido", cascade = ALL, orphanRemoval = true)` con `@OrderBy("posicion")`, perezosa por defecto. Las
líneas no cambian después de crear el pedido (`copiarDesde` solo copia el estado). Se cargan con `join fetch` en `buscar` y
`deCliente`.

## Paso 5. Adaptadores y unidad de trabajo

`AlmacenJpa` (pedidos, inventario, peticiones y bandeja), `CatalogoJpa`, `UnidadDeTrabajoJpa` (traduce
`OptimisticLockingFailureException` y `PessimisticLockingFailureException` a `ConflictoDeConcurrencia`) y `Mensajero`
(`@Scheduled`, entrega la bandeja con `for update skip locked`).

Dos cosas que se descubrieron al ejecutarlo:

1. `getResultStream()` fuera de una transacción devolvió `PSQLException: This ResultSet is closed` (el `EntityManager`
   compartido se cierra al terminar la llamada, antes de recorrer el flujo). `GET /pedidos/{id}` respondía 500. Se cambió a
   `getResultList()`.
2. El conflicto de versión llega al confirmar, y `UnidadDeTrabajoJpa` lo traduce: probado en
   `elConflictoDeVersionLlegaAlCasoDeUsoQueReintentaYNoVendeLaUnidadDosVeces` (dos lecturas: hubo reintento).

Dos defensas contra el interbloqueo, y por qué las dos: `order_updates` evita el interbloqueo entre pedidos con productos
cruzados (los `UPDATE` salen ordenados por id); la traducción de `PessimisticLockingFailureException` a `ConflictoDeConcurrencia`
es la red de seguridad si aparece otro orden de bloqueo, porque así el caso de uso se reintenta. El banco del curso eligió solo la primera.

## Paso 6. Raíz de composición

`Configuracion` declara `ServicioDePedidos` con `@Bean`; el núcleo no tiene ni una anotación de Spring (la prueba de
arquitectura de la sesión 08 lo confirma: solo importa `java.*` y el propio núcleo). El reloj se trunca a microsegundos:
es la precisión de PostgreSQL (ver taller 08, paso 2).

## Paso 7. El SQL de un pedido con dos líneas

`POST /pedidos` con 2 teclados y 1 ratón. Sentencias en el orden en que salieron (registro de `org.hibernate.SQL`):

| # | Sentencia | Cuándo y por qué |
|---|---|---|
| 1 | `select pedido_id from peticion_procesada where clave = ?` | Al empezar: ¿la clave ya se atendió? |
| 2–3 | `select … from producto where id = ?` (×2) | Precio de cada línea, `CatalogoJpa` |
| 4–5 | `select … from existencias where producto = ?` (×2) | Las existencias de cada producto; quedan en el contexto de persistencia con su `version` |
| 6 | `select … from pedido where id = ?` | `guardar` busca si el pedido ya existe para decidir entre insertar y actualizar (no existe) |
| 7 | `insert into pedido (cliente, creado_en, estado, version, id) values (…)` | Sale en el `flush` que fuerza `registrar` (la clave foránea de `peticion_procesada` exige el pedido antes) |
| 8–9 | `insert into linea_de_pedido (…) values (…)` (×2) | En cascada, en el mismo `flush` |
| 10–11 | `update existencias set unidades=?, version=? where producto=? and version=?` (×2) | **También en ese `flush`**, no al final: el `flush` envía todo lo pendiente. Ordenadas por id gracias a `order_updates` |
| 12 | `insert into peticion_procesada (clave, pedido_id) values (?, ?) on conflict do nothing` | SQL nativo: si devuelve 0 filas, otra transacción ya usó la clave y el caso de uso reintenta |
| 13 | `insert into evento_pendiente (…)` | Al confirmar la transacción |
| — | `select * from evento_pendiente … for update skip locked` y `delete from evento_pendiente where id=?` | Medio segundo después, en el hilo `scheduling-1`: el mensajero |

Nota: con `format_sql: true` el registro muestra `for update skip locked` partido como `for` / `updateskip locked`. Es solo el
formateador del registro: la sentencia se ejecuta bien (el correo salió y la fila se borró).

## Paso 8. N+1

Prueba `listarPedidosConSusLineasEsUnaSolaConsulta`, con estadísticas de Hibernate (`getPrepareStatementCount`) y 10 pedidos
de 2 líneas:

| Operación | Consultas |
|---|---|
| Consultar un pedido con sus líneas (`buscar`, con `join fetch`) | 1 |
| Listar 10 pedidos y recorrer sus líneas, **sin** `join fetch` (primera versión) | 11 (1 + N) |
| Listar 10 pedidos con sus líneas, con `join fetch` (versión final de `deCliente`) | 1 |

## Paso 9. Dos peticiones simultáneas por la última unidad

Se pone `monitor` en 1 unidad y se lanzan dos `curl` en paralelo con claves distintas, repitiéndolo 10 veces. En cada repetición:
un 201 y un 422 (o 409 si se agotan los reintentos), `existencias.unidades = 0` y exactamente un pedido con monitor entre los
dos clientes. La primera repetición manual dio `201` y `422`, unidades `0`, pedidos con monitor `1`. Además, la prueba
`muchosHilosNoVendenMasDeLoQueHay` (8 hilos × 15 pedidos sobre 60 teclados) comprueba vendidas + quedan = 60 sin errores que
no sean conflicto; en una ejecución: 30 creados, 29 sin existencias, 61 rendidos por conflicto. Con tres reintentos sin
espera y mucha contención, muchos se rinden: por eso el reintento en producción lleva espera con azar.

## Paso 10. Las pruebas anteriores

`TodasLasPruebas` de la sesión 06 se ejecuta (137 comprobaciones, 0 fallos) sobre los mismos archivos que compila
este proyecto: no se tocó ninguno.

## Criterios de aceptación

| Criterio | Resultado |
|---|---|
| El dominio y la aplicación no tienen cambios | El proyecto compila `06/taller/solucion-de-referencia/src`; `diff -r` 05↔06 del núcleo sin diferencias |
| El núcleo no importa nada de `jakarta` ni de `org.springframework` | `grep` sobre el núcleo: ninguno |
| El esquema está escrito a mano y Hibernate no lo genera | `ddl-auto: none`, `schema.sql` |
| Dos peticiones por la última unidad venden una sola | Paso 9 |
| Listar pedidos con líneas no genera una consulta por pedido | Paso 8: 1 consulta |
| Las pruebas anteriores pasan sin modificarse | Paso 10 |
