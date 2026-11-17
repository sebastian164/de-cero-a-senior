# Análisis del taller 01 (¿qué es un pool?)

Los números salen de `resultados/resultados.md`, que escribe `Taller01Test` al ejecutarse (`mvn test`; la carpeta `resultados/` se crea sola) contra PostgreSQL 17 con `max_connections=30`
(HikariCP 7.1.0, controlador 42.7.x). Los de esta máquina, en una corrida: se citan para ver la proporción, no como valor fijo.

## Tabla de los pasos 1 a 3 (mil `select 1`)

| Variante | Total | Por operación |
|---|---|---|
| `DriverManager`, una conexión nueva cada vez | ~3000 ms | ~3 ms |
| `DriverManager`, una sola conexión reutilizada | ~130 ms | ~0,13 ms |
| HikariCP, `getConnection()` + `close()` cada vez | ~130 ms | ~0,13 ms |

**Explicación.** Abrir una conexión es un viaje de ida y vuelta por la red, el protocolo de arranque de PostgreSQL (que crea un proceso por
conexión) y la autenticación. Una consulta trivial cuesta una fracción de eso: por eso abrir cada vez es unas veinte veces más lento.
HikariCP paga la apertura una vez y después presta y recibe de vuelta: el uso del código es el de "abrir y cerrar" pero el coste es el de
"reutilizar". La diferencia entre reutilizar a mano y usar el pool es casi nula en el tiempo; el pool añade lo que a mano no hay: reparto
entre hilos, límite, renovación y detección de conexiones rotas.

## Paso 4: lo que ve PostgreSQL

Abriendo cada vez, en `pg_stat_activity` hay como mucho una conexión a la vez (cada una vive milisegundos). Con el pool hay diez desde el
principio y se quedan abiertas todo el tiempo: el pool **reserva** conexiones aunque no las use. Es la otra cara de la moneda: se gana
tiempo y se paga con conexiones ocupadas en la base, por eso hay que dimensionarlo (sesión 02).

## Paso 5: cincuenta hilos contra treinta conexiones

Sin pool, los hilos que llegan cuando ya hay conexiones de sobra reciben el error de PostgreSQL, copiado tal cual de ese archivo (298 de 2000 consultas en esa corrida):

    PSQLException: FATAL: sorry, too many clients already

Con un pool de diez, los cuarenta hilos que no tienen conexión **esperan su turno** (dentro del
`connectionTimeout`): las 2000 consultas terminan sin ningún error. El pool convierte un fallo en una cola.

## Paso 6: la undécima conexión

Con un pool de diez y diez conexiones tomadas y no devueltas, la undécima espera exactamente `connectionTimeout` (30 000 ms por defecto) y
falla, copiado tal cual de ese archivo (tardó 30 007 ms):

    java.sql.SQLTransientConnectionException: HikariPool-3 - Connection is not available, request timed out after 30006ms (total=10, active=10, idle=0, waiting=0)

El mensaje trae el estado del pool y es la firma de una fuga o de una retención (sesiones 03 y 04).

## Pasos 7 a 9

- **Registro en DEBUG:** al arrancar se imprime la configuración completa (`HikariConfig`), se agregan conexiones hasta el mínimo
  (`connection-adder`) y el vigilante (`housekeeper`) escribe cada treinta segundos `Pool stats (total=…, idle=…, active=…, waiting=…)`.
- **Dos `HikariDataSource` por error:** PostgreSQL ve 20 conexiones, no 10: cada pool es independiente y reserva las suyas. Un pool por
  clase multiplica las conexiones y puede agotar el límite de la base sin que nadie haya "pedido" tantas.
- **Cerrar el pool:** con el pool abierto hay 10 conexiones en la base; tras `close()`, 0. Si el proceso muere sin cerrarlo, el sistema
  operativo cierra los sockets y PostgreSQL las libera al detectar la desconexión (0 en la prueba), pero un corte de red o un
  `kill -9` en otra máquina las deja colgadas hasta que la base lo nota.

## Por qué `close()` no cierra

Lo que el código recibe de `getConnection()` no es la conexión física sino un **envoltorio** (un `ProxyConnection`) que apunta a ella.
Su `close()` está reemplazado: no cierra el socket, revierte lo que quedó pendiente, restablece el estado (autocommit, solo lectura,
aislamiento) y devuelve la conexión física al pool para que otro hilo la tome. La conexión real solo se cierra cuando el pool decide
retirarla (`maxLifetime`, `idleTimeout`, validación fallida) o cuando se cierra el pool. Por eso se escribe `try-with-resources` como si
fuera una conexión normal: el mismo código sirve con y sin pool, y por eso olvidar `close()` es una fuga: el envoltorio nunca vuelve.
