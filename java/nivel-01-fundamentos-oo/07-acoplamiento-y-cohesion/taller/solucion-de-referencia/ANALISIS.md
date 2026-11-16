# Taller — Auditoría de acoplamiento y cohesión (solución de referencia)

Proyecto auditado: el propio repositorio del curso, en particular el banco de `hexagonal/proyecto/` (Java puro, unas 1.800 líneas con sus pruebas). Es código abierto para quien tenga el repo y no hay nada confidencial. Las rutas se citan tal cual. El código del punto 5 está en `acoplamiento-temporal/`.

## 1. Clase con cohesión baja

`hexagonal/proyecto/src/banco/adaptadores/salida/memoria/AlmacenEnMemoria.java` implementa cinco puertos a la vez: `RepositorioDeCuentas`, `RegistroDeMovimientos`, `OrdenesProcesadas`, `PublicadorDeEventos` y `UnidadDeTrabajo`. Sus grupos de métodos casi no se tocan entre sí:

| Grupo | Campos que usa |
|---|---|
| Cuentas (buscar, guardar con versión) | `cuentas` |
| Movimientos (registrar, listar por cuenta) | `movimientos` |
| Órdenes procesadas (idempotencia) | `ordenes` |
| Eventos (bandeja de salida) | `bandejaDeSalida` |
| Transacción (`ejecutar`, confirmar) | todos, a través de `Transaccion` |

Lo único que los une es la transacción: todo lo pendiente se confirma de una vez. Cómo la dividiría: un `AlmacenEnMemoria` que solo administra la transacción y los datos confirmados, y cuatro adaptadores pequeños (`CuentasEnMemoria`, `MovimientosEnMemoria`, etc.) que reciben el almacén y operan sobre su parte. Matiz honesto: en este proyecto la clase simula una base de datos, y una base de datos real también guarda todas las tablas; juntarlo fue una decisión consciente para que la transacción sea simple. Lo dividiría el día en que un adaptador de memoria cambie sin que cambien los demás.

## 2. Tren de llamadas de tres o más niveles

`hexagonal/proyecto/src/banco/adaptadores/entrada/http/ServidorHttp.java`, línea 83:

```java
String[] ruta = intercambio.getRequestURI().getPath().split("/");
```

`HttpExchange` → `URI` → `String` → `String[]`. `despachar` conoce la estructura interna de la petición del JDK y cómo partir la ruta. Lo reemplazaría con un método privado `tramosDeLaRuta(HttpExchange)` en el mismo `ServidorHttp` (o un pequeño valor `Ruta` con `es("cuentas", "*")`, que además absorbería el método `es` actual). Se trata de un tren sobre objetos de valor del JDK, el caso más benigno de la ley de Deméter: se reemplaza por legibilidad y para que el ruteo se pruebe sin un `HttpExchange`, no porque haya un acoplamiento peligroso.

## 3. Dependencia de estado global

`hexagonal/proyecto/pruebas/banco/Comprobar.java` lleva el conteo de pruebas en `private static int total` y `private static int fallos`, mutables y compartidos por todas las suites que `TodasLasPruebas` ejecuta en la misma JVM.

Error que podría causar: los contadores no son atómicos. Si una suite (por ejemplo `PruebasDeConcurrencia`, que ya usa un `ExecutorService`) llamara a `Comprobar.igual` desde varios hilos, `fallos++` podría perder incrementos: una falla real no se contaría y `System.exit(Comprobar.fallos() == 0 ? 0 : 1)` terminaría en 0, es decir, una integración continua en verde con una prueba fallando. Hoy no ocurre porque las comprobaciones se hacen desde el hilo principal; la clase no lo garantiza. Además, ejecutar una suite dos veces en la misma JVM acumula conteos. Corrección: un objeto `Resultados` que cada suite recibe, o al menos `AtomicInteger`.

## 4. El archivo que más aparece en el historial

Ejecutado en este repo:

```
$ git log --format= --name-only | sort | uniq -c | sort -rn | head -5
   9 ROADMAP.md
   8 README.md
   7 PROGRESO.md
   3 java/nivel-09-sistemas-distribuidos/04-consistencia-sagas-outbox/README.md
   3 java/nivel-09-sistemas-distribuidos/03-mensajeria-y-eventos/README.md
```

Encabezan `ROADMAP.md`, `README.md` y `PROGRESO.md`. No es cohesión alta de una pieza importante: son índices que agregan información de todos los módulos, y cualquier módulo nuevo (Kafka, HikariCP, hexagonal) obliga a tocarlos. Es la definición de baja cohesión aplicada a documentación: cambian por muchas razones distintas. Es un costo aceptable (son tablas de contenido y cada cambio es una línea), pero explica los conflictos de merge cuando varias ramas agregan módulos a la vez. Un índice generado a partir de los README de cada módulo lo eliminaría. Con solo 19 commits el historial es corto y la conclusión es débil.

## 5. Acoplamiento temporal

`acoplamiento-temporal/antes/Main.java`: `ClienteCorreo` exige `configurar()`, luego `conectar()`, luego `enviar()`. Llamar `enviar()` primero compila y falla en ejecución (salida real: `Orden incorrecto detectado tarde: No hay conexión`).

`acoplamiento-temporal/despues/`: cada paso devuelve el tipo del paso siguiente. `Configuracion` (record validado) solo tiene `conectar()`, que devuelve una `Conexion`; `Conexion` es la única que tiene `enviar()` y su constructor no es público. Los dos órdenes incorrectos no compilan (compruébalo desde `acoplamiento-temporal/` con `javac -d out despues/correo/*.java no-compila/EnviarSinConectar.java`, y lo mismo con el otro archivo):

- `no-compila/EnviarSinConectar.java`: `error: cannot find symbol` (`Configuracion` no tiene `enviar`).
- `no-compila/ConexionSinConfigurar.java`: `error: Conexion(Configuracion) is not public in Conexion`.
