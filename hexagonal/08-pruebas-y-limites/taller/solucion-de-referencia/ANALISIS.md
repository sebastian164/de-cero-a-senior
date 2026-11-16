# Taller 08 — Pruebas por capa, regla de dependencias y límites

Esta carpeta es el servicio de la sesión 06 más los contratos, el requisito de aprobación y la medición por capa.
`jpa/` es un proyecto Maven que compila **ese** núcleo y los adaptadores JPA de la sesión 07 (sin copiarlos) y
ejecuta los contratos contra PostgreSQL 17 con Testcontainers 2.0.5 (puerto libre). `romper-arquitectura.sh` es el paso 3.
Versiones: JDK 21.0.13, Spring Boot 4.1.1, Hibernate 7.4.5.Final, PostgreSQL 17.12.

## Paso 1. Contratos contra memoria

`pruebas/tienda/ContratoDelRepositorioDePedidos.java` y `ContratoDelInventario.java`. Cada uno recibe el puerto **y su
unidad de trabajo**: la cláusula de concurrencia necesita dos transacciones (la segunda en otro hilo).

| Cláusula de `RepositorioDePedidos` | Cláusula de `Inventario` |
|---|---|
| Lo guardado se recupera igual, con sus líneas en orden | Lo guardado se recupera igual |
| La fecha se recupera exacta, al microsegundo | Un producto sin registro devuelve vacío |
| Buscar algo que no existe devuelve vacío | Lo que se entrega es una copia |
| Los identificadores no se repiten | Guardar lo leído en la misma transacción actualiza |
| Lo que se entrega es una copia | Lo guardado en una transacción revertida no queda |
| Los pedidos de un cliente son solo los suyos, del más antiguo al más reciente | Guardar lo leído antes de que otro lo cambiara falla con conflicto |
| Lo guardado en una transacción revertida no queda | Tras el conflicto queda solo el cambio de quien confirmó primero |
| Guardar lo leído antes de que otro lo cambiara falla con conflicto | |

Resultado con `AlmacenEnMemoria`: los dos, lista vacía. Como control, `InventarioIngenuo` con `SinTransaccion` incumple
exactamente las tres cláusulas de revertir y de concurrencia.

## Paso 2. Los mismos contratos contra JPA y PostgreSQL

`jpa`, `ContratosJpaTest`: `Contrato de inventario contra JPA: []`, `Contrato de pedidos contra JPA: []`.

Una cláusula falló en uno y no en otro, y hubo que decidir. La primera versión guardaba un pedido creado a las
`10:00:00.123456789` y exigía leerlo truncado a microsegundos:

```
memoria: [la fecha se conserva con precisión de microsegundos]     (devolvía los nanosegundos tal cual)
JPA:     [la fecha se conserva con precisión de microsegundos]     (PostgreSQL redondea: .123457, no .123456)
```

Los dos tenían "razón" a su manera: la memoria guarda lo que le dan y PostgreSQL solo tiene microsegundos y **redondea**.
Decisión: el contrato promete microsegundos y quien fabrica las fechas no da más: `RelojDelSistema` y el reloj de
`Configuracion` (sesión 07) truncan a microsegundos, y la cláusula pasó a "la fecha se recupera exacta, al microsegundo".
Con esa precisión, los dos adaptadores cumplen. Sin el contrato, un pedido creado en memoria y uno leído de la base habrían
tenido fechas distintas y ninguna prueba con memoria lo habría visto.

## Paso 3. Romper la regla a propósito

`romper-arquitectura.sh` copia la solución, añade un `import` prohibido, compila (compila: es un `import` válido) y ejecuta
solo la prueba de arquitectura. Salida:

```
== Violación: dominio-importa-aplicacion (import tienda.aplicacion.VistaDePedido; en tienda/dominio/Pedido.java)
  FALLA el dominio solo depende de sí mismo y de java.* sin entrada y salida: esperaba [] y obtuve [Pedido.java -> tienda.aplicacion.VistaDePedido]
== Violación: aplicacion-importa-adaptador (import tienda.adaptadores.salida.memoria.AlmacenEnMemoria; en tienda/aplicacion/ServicioDePedidos.java)
  FALLA la aplicación solo depende del dominio, de sí misma y del JDK sin entrada y salida: esperaba [] y obtuve [ServicioDePedidos.java -> tienda.adaptadores.salida.memoria.AlmacenEnMemoria]
== Violación: entrada-importa-salida (import tienda.adaptadores.salida.memoria.AlmacenEnMemoria; en tienda/adaptadores/entrada/consola/Consola.java)
  FALLA los adaptadores de entrada no conocen a los de salida: esperaba [] y obtuve [Consola.java -> tienda.adaptadores.salida.memoria.AlmacenEnMemoria]
== Violación: entrada-usa-puerto-de-salida (import tienda.aplicacion.puertos.salida.Inventario; en tienda/adaptadores/entrada/http/ServidorHttp.java)
  FALLA los adaptadores de entrada solo usan puertos de entrada: esperaba [] y obtuve [ServidorHttp.java -> tienda.aplicacion.puertos.salida.Inventario]
La prueba de arquitectura avisó las cuatro veces.
```

## Paso 4. Pruebas por capa, con números reales

Medido por `TodasLasPruebas` (JDK 21.0.13, Apple Silicon) y por Surefire para JPA. Una ejecución representativa:

| Capa | Grupo | Comprobaciones | Tiempo |
|---|---|---|---|
| Dominio | `PruebasDelDominio` (con la aprobación) | 45 | 21 ms |
| Aplicación | `PruebasDeLaAplicacion` | 27 | 12 ms |
| Aplicación | `PruebasDeTransacciones` | 25 | 2 ms |
| Aplicación | `PruebasDeConcurrencia` (incluye 8 hilos × 40 pedidos) | 22 | 20 ms |
| Adaptador de entrada | `PruebasDeAdaptadores` (HTTP real y consola) | 29 | 129 ms |
| Adaptador de salida, memoria | Contratos | 3 | 2 ms |
| Arquitectura | `PruebasDeArquitectura` | 5 | 5 ms |
| Adaptador de salida, JPA | `ContratosJpaTest` (2 contratos) | 2 pruebas JUnit | ≈ 3,4 s (arranque de PostgreSQL en contenedor y de Spring incluidos) |
| Adaptador de salida, JPA | `TiendaJpaTest` de la sesión 07 | 6 pruebas JUnit | ≈ 3,5 s |

Las 156 comprobaciones en memoria tardan unos 0,2 s; las 8 pruebas con base real, unos 7 s. La pirámide sale sola.

## Paso 5. Una regla y cuántas pruebas la cubren

"No se vende más de lo que hay":

| Capa | Pruebas |
|---|---|
| Dominio | Última unidad, una de más, no descuenta al rechazar, pedido con una línea imposible no toca las otras, producto sin registro, líneas repetidas cuentan juntas, reservar y liberar (8) |
| Aplicación | Sin existencias se rechaza y ningún rechazo descontó; reserva y devolución al cancelar |
| Concurrencia | Última unidad con dos pedidos simultáneos; invariante vendidas + quedan con 8 hilos |
| Adaptador de entrada | 422 por HTTP, `rechazado:` por consola |
| JPA | Última unidad con interferencia; muchos hilos; dos `curl` simultáneos (sesión 07) |

No había ninguna que solo se probara por HTTP.

## Paso 6. Requisito nuevo: más de diez unidades de un producto necesitan aprobación

| Archivo | Capa | Cambio |
|---|---|---|
| `dominio/EstadoDePedido.java` | Dominio | `REQUIERE_APROBACION` |
| `dominio/Pedido.java` | Dominio | `UNIDADES_SIN_APROBACION = 10`, `crear` decide el estado, `aprobar()`; cancelar lo admite |
| `aplicacion/puertos/entrada/AprobarPedido.java` | Aplicación | Nuevo puerto |
| `aplicacion/ServicioDePedidos.java` | Aplicación | `aprobar` con reintento, unidad de trabajo y evento `PedidoAprobado` |
| `adaptadores/entrada/http/ServidorHttp.java` | Adaptador | `POST /pedidos/{id}/aprobacion` |
| `Main.java` | Composición | Un argumento más al servidor HTTP |
| `PruebasDelDominio`, `PruebasDeLaAplicacion`, `PruebasDeAdaptadores` | Pruebas | 8 + 6 + 2 comprobaciones |

Los adaptadores JPA de la sesión 07 **no** se tocaron: el estado se guarda como texto (`EnumType.STRING`, `varchar(32)`), y
los contratos contra PostgreSQL siguen pasando con el núcleo nuevo. La primera prueba que pasó fue la del dominio
("once sí"), sin levantar nada. El tiempo que tarde cada quien depende de cada quien; lo comparable es el número de archivos: dos del dominio, dos de la aplicación y uno de cada adaptador de
entrada que quiera exponer la operación.

## Paso 7. Puertos con una sola implementación

| Puerto | Implementaciones | ¿Lo conservo? |
|---|---|---|
| `Reloj` | `RelojDelSistema`, el `@Bean` de JPA y un reloj fijo en cada prueba | Sí: las pruebas lo necesitan |
| `Catalogo` | `CatalogoEnMemoria`, `CatalogoJpa` y el catálogo roto de la prueba del 500 | Sí |
| `PeticionesProcesadas` | `AlmacenEnMemoria` y `AlmacenJpa`; ningún doble | Sí, pero por otra razón: es una pieza de la transacción, no una integración. Podría ser un detalle del repositorio de pedidos; se mantiene porque el contrato de idempotencia es distinto del de pedidos |
| `PublicadorDeEventos` | Los dos almacenes (bandeja) | Sí: en la ruta de Kafka será otro adaptador |

## Paso 8. La tabla de motivos de cancelación

No con esta arquitectura. Es un CRUD sin reglas: listar y editar textos. Un controlador con `JdbcTemplate` (o un
repositorio de Spring Data) y una prueba de extremo a extremo bastan. Si un día aparece una regla ("un motivo en uso no se
borra"), esa regla se lleva al dominio de pedidos, no la tabla entera.

## Paso 9. Valoración

**Qué me dio.** Las reglas se prueban en milisegundos: 156 comprobaciones en unos 0,2 s, incluida una prueba con 8 hilos. El
defecto del taller 01 (existencias descontadas de un pedido rechazado) se arregló dos veces y en dos sitios con nombre: en el
dominio (`Reserva` comprueba todo antes de descontar) y en la unidad de trabajo. Pasar de memoria a PostgreSQL fue escribir
adaptadores: el núcleo de la sesión 06 compiló sin cambios dentro de Spring. Un requisito nuevo tocó dos archivos del
dominio y la persistencia no se enteró. Y los contratos encontraron una diferencia real (la precisión de las fechas) que
ninguna prueba con memoria habría visto.

**Qué me costó.** Cinco clases JPA y traducciones en los dos sentidos para cuatro tablas. Llevar la versión en el almacén y
no en el dominio obliga a leer y escribir en la misma transacción, y es una regla implícita que nadie ve en una firma. Los
contratos con concurrencia necesitan hilos y unidades de trabajo, y depurar un contrato que falla dentro de otro hilo es
incómodo. El JSON a mano del adaptador HTTP del JDK es frágil. Hubo errores que solo aparecieron al ejecutar contra la base
real: `getResultStream` fuera de transacción, y en el banco, interbloqueos que obligaron a ordenar los `UPDATE`. La
indirección es real: para seguir una petición hay que saltar por cinco interfaces.

**Dónde no la usaría.** En un panel interno de configuración, en un servicio que solo reenvía mensajes sin reglas, o en un
prototipo para validar una idea en una semana: ahí el coste de puertos, traducciones y contratos no se recupera.
