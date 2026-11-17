# Taller 04 — MongoDB en el servicio de pedidos hexagonal

Parte del servicio de pedidos que resolvió el [módulo hexagonal](../../../../hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia/) (`tienda.*`: dominio, casos de uso, puertos, contratos) y le conecta MongoDB **sin tocarlo**. Lo medido queda en `salida/` al ejecutar las pruebas (esa carpeta no se publica).

## Cómo se garantiza "el dominio y la aplicación no cambiaron"

El núcleo no se copia: el `pom.xml` lo compila desde donde está (`build-helper:add-source` sobre `hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia/src`) y toma sus contratos (`add-test-source` sobre `.../pruebas`). Lo que esta solución añade vive en paquetes propios y solo en dos sitios:

| Dónde | Qué |
|---|---|
| `tienda.adaptadores.salida.mongo` | Los adaptadores de MongoDB de los puertos del núcleo: `UnidadDeTrabajoMongo`, `PedidosMongo`, `InventarioMongo`, `CatalogoMongo`, `PeticionesMongo`, `BandejaMongo`, `EsquemaMongo` (más `Op` y `Errores`) |
| `nosql.aplicacion` | Casos de uso y puertos **nuevos** que el núcleo no tenía y que piden las sesiones siguientes: `ConsultarProducto` y `VistaDeProducto` (lo que cachea la 05), `CambiarPrecioDeProducto`, `CrearPedidoAcotado`, `PoliticaDeReintento` y `ServicioDePedidosConEspera` |
| `nosql.Composicion` | La raíz de composición que elige memoria o MongoDB con un parámetro (`pedidos.adaptador` o `PEDIDOS_ADAPTADOR`) |

`ArquitecturaTest` lo comprueba de tres maneras: (1) `com.mongodb`, `org.bson`, `io.lettuce` y `com.sun.net.httpserver` solo aparecen bajo `adaptadores/` y en las raíces de composición, mirando el núcleo y este módulo (0 violaciones; la prueba exige además que al menos 6 archivos de adaptadores sí los importen, para que no pase por mirar vacío); (2) ningún archivo de este módulo declara los paquetes `tienda.dominio`, `tienda.aplicacion` ni `tienda.aplicacion.puertos`; (3) el único cambio de la aplicación es el del paso 7, y es una **copia** con otro nombre: `diff -u` entre `ServicioDePedidos` y `ServicioDePedidosConEspera` se guarda en `salida/diff-reintento.txt` y solo toca el paquete, los `import` de lo que antes era del mismo paquete, el nombre de la clase, la constante `INTENTOS` (que pasa a ser una `PoliticaDeReintento` inyectada) y el cuerpo de `conReintento`. El `ServicioDePedidos` original sigue intacto y es el que se mide en el paso 6.

Lo que el núcleo no podía dar y se añade **al lado**, sin modificarlo: el tope de 50 líneas (el dominio no lo tiene) es el decorador `CrearPedidoAcotado` más el validador de MongoDB; el precio y las existencias que ve el exterior son una vista (`ConsultaDeProductos` sobre los puertos `Catalogo` e `Inventario`).

## Paso 1. La unidad de trabajo

`UnidadDeTrabajoMongo` es el único sitio con `startSession`, `startTransaction`, `commit` y `abort`. Opciones de la transacción: lectura `SNAPSHOT`, escritura `MAJORITY`, lectura del primario. Una unidad anidada se une a la abierta (el contrato y `ServicioDePedidos.crear` anidan). No usa `withTransaction` del controlador: este reintenta solo hasta 120 s, y la decisión de reintentar es de la aplicación (pregunta 2 de la sesión).

## Paso 2. Repositorios y modelo

Los documentos son los del taller 01 (pedido con `clienteId`, `estado`, `fecha`, `total`, `lineas[]` con `productoId`, `nombre` copiado, `cantidad` y `precio` copiado, `historial[]` con tope 20, `version`), con un validador `$jsonSchema` (líneas de 1 a 50, historial de hasta 20, estado en el enumerado del núcleo, total y cantidades válidos) y los índices del taller 02 (`{clienteId, fecha:-1, _id:-1}` y `{estado, fecha}`). Dos diferencias con 01, ambas por seguir al núcleo y no al enunciado del curso: los estados son `REQUIERE_APROBACION`, `PENDIENTE`, `CONFIRMADO` y `CANCELADO` (el núcleo no tiene `PAGADO` ni `ENVIADO`: "pagar" es `confirmar`, y no hay envío) y los ids son cadenas (`pedido-` + `ObjectId`, que se ordena por creación y no necesita un documento contador caliente).

La **versión** no viaja en el dominio (el `Pedido` del núcleo no la tiene): `PedidosMongo` e `InventarioMongo` anotan la versión que leyeron de cada objeto (un `WeakHashMap` por identidad) y la exigen al guardar con `findOneAndUpdate({_id, version})`; si no encaja, `ConflictoDeConcurrencia`. Lo que no estaba previsto: **BSON guarda milisegundos** y el contrato del núcleo promete microsegundos; la fecha se guarda dos veces, `fecha` (`Date`, para ordenar e indexar) y `fechaMicros` (la exacta).

## Paso 3. Los contratos, sin modificar

`ContratoDelRepositorioDePedidos.incumplimientos` y `ContratoDelInventario.incumplimientos` (los del módulo hexagonal) contra `AlmacenEnMemoria` y contra MongoDB real: **lista vacía en los cuatro casos** (`salida/contratos.txt`). Incluyen la cláusula de concurrencia ("guardar lo que se leyó antes de que otro lo cambiara falla con conflicto"), que sale por dos caminos distintos en MongoDB (la versión no encaja, o el servidor aborta por `WriteConflict`) y llega siempre como `ConflictoDeConcurrencia`.

## Paso 4. Errores traducidos

`Errores` es el único sitio que traduce. Etiqueta `TransientTransactionError` (`WriteConflict` y compañía) o `DuplicateKey` → `ConflictoDeConcurrencia`; `MongoTimeoutException` y `MongoSocketException` → `ErrorDeAlmacenamiento` (no se reintentan como un choque: el almacén no está); cualquier otra `MongoException` → `ErrorDeAlmacenamiento` con un mensaje genérico. Probado: clave duplicada, y una conexión a un puerto sin servidor dentro y fuera de transacción (`erroresTraducidos`). La versión que no coincide la cubre el contrato.

## Paso 5. Un fallo a mitad no deja nada

Con `PedidosQueFallan` (del módulo hexagonal) envolviendo el repositorio real, `crear` falla entre descontar y guardar. Con MongoDB: existencias intactas (10), 0 pedidos, 0 claves de idempotencia, 0 eventos; y la misma clave se puede reintentar con éxito después (no quedó registrada).

## Pasos 6 y 7. 200 pedidos concurrentes sobre tres productos con 120 unidades cada uno

| Adaptador | Política | Realizados | Rechazados por existencias | Abandonados por conflicto | Tiempo |
|---|---|---|---|---|---|
| Memoria | original: 3 intentos sin espera | 120 | 20 a 63 | 17 a 60 | ~20 ms |
| Memoria | espera creciente con azar, 50 intentos, tope 1 s | 120 | 80 | **0** | ~50 ms |
| MongoDB | original: 3 intentos sin espera | **4 o 5** | 0 | **195 o 196** | ~300 ms |
| MongoDB | espera creciente con azar, 15 intentos, tope 100 ms | 69 a 114 | 0 | 86 a 131 | ~1,1 s |
| MongoDB | espera creciente con azar, 50 intentos, tope 1 s | **120** | 80 | **0** | 1,4 a 2,1 s |

(Rangos de varias ejecuciones; la última queda en `salida/servicio.txt` al ejecutar.) Con MongoDB, las existencias de cada producto cuadran siempre (120 − realizados), hay un pedido y un evento por cada realizado, y nunca se vende de más. Las tres variantes son **correctas**; la diferencia es cuánta gente se va con un 409 que no merecía: con la política original, 195 de 200 compradores con stock disponible. **Decisión:** espera creciente con azar completo, 50 intentos y tope de 1 s. Es lo más barato que cambia el resultado (un cambio de política, no de dominio) y lo correcto aquí porque el conflicto es transitorio y es contienda sobre tres filas calientes, no un error del cliente. Lo que cuesta: latencia de cola (hasta ~2 s cuando todos pelean por lo mismo) y presión sobre MongoDB; con más contienda real (un producto estrella) la alternativa es cambiar el modelo: descontar con `$inc` condicional (sesión 03) en lugar de leer-modificar-escribir con versión, que el puerto `Inventario` del núcleo no permite sin cambiarlo.

## Paso 8. Un parámetro, nada más

`Composicion.elegir("memoria" | "mongo", ...)`; `ServicioEnAmbosAdaptadoresTest` ejecuta el mismo escenario (precio copiado, idempotencia, confirmar, cancelar, historial) con los dos y los comparte `Sistema`, que solo expone puertos de entrada.

## Criterios de aceptación

| Criterio | Dónde |
|---|---|
| El dominio y la aplicación no cambiaron | Compilados desde el original; `ArquitecturaTest` (sin clases en sus paquetes) y `salida/diff-reintento.txt` (la única copia, la del paso 7) |
| El contrato pasa contra memoria y contra MongoDB | `salida/contratos.txt`: los dos contratos del módulo hexagonal, sin modificar, en los dos adaptadores |
| `com.mongodb` solo aparece en el adaptador | `ArquitecturaTest.comMongodbSoloEnElAdaptador` |
| Un fallo a mitad no deja datos | `unFalloAMitadNoDejaNada` (productos, pedidos, peticiones y bandeja) |
| Bajo concurrencia, el total de existencias cuadra | `doscientosPedidosSobreTresProductos`, 120 − realizados en cada producto |
