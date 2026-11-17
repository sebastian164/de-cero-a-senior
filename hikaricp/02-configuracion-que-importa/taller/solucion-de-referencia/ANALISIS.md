# Análisis del taller 02 (configuración que importa)

Los números salen de `resultados/resultados.md` (lo escribe `medir.sh` al ejecutarse; k6 2.4.0, 50 usuarios; PostgreSQL 17 con `max_connections=30`,
en Docker en un portátil con 12 núcleos visibles). Cada punto dura 10 a 15 s, no un minuto: la forma de las curvas es la que importa,
no el valor exacto, que varía de una corrida a otra y con lo que más corra en la máquina.

## Pasos 1 y 2: los valores por defecto

Con `logging.level.com.zaxxer.hikari.HikariConfig: debug` el registro lista la configuración al arrancar: `maximumPoolSize=10`,
`minimumIdle=10` (igual al máximo: tamaño fijo), `connectionTimeout=30000`, `maxLifetime=1800000`, `keepaliveTime=120000`,
`idleTimeout=600000`, `validationTimeout=5000`, `leakDetectionThreshold=0`. Lo mismo se ve en `hikaricp.connections.max` y `.min`.
Con 50 usuarios y una consulta de 20 ms, el pool de 10 da unas 440 peticiones/s (el techo teórico es 10 / 0,020 s = 500).

## Paso 3: el barrido de tamaños (50 usuarios, base con 12 núcleos)

Con `pg_sleep(0,02)` la consulta **no gasta CPU**: el pool solo limita cuántas se esperan a la vez, y el rendimiento sube casi lineal
(2 → ~85/s, 5 → ~220, 10 → ~450, 20 → ~900, 28 → ~1150). Esa prueba **no demuestra** que más grande sea mejor: demuestra que una
espera de 20 ms es barata para la base. Con una consulta que sí trabaja (`/cpu`, que ordena y agrega datos), el rendimiento deja de crecer
en cuanto se llena el trabajo útil de la base y por encima solo crece la latencia. En la corrida limitada a un núcleo (paso 5) se ve nítido:
2 conexiones dan el máximo (~57 peticiones/s, p95 970 ms) y 28 dan menos (~46/s, p95 1800 ms): más conexiones **sobrecargan** una base
que ya estaba al límite (cambios de contexto, memoria, bloqueos internos). El máximo está en pocas conexiones; por encima, nada sube y la
latencia empeora. La gráfica está en `resultados/grafica.svg`.

## Paso 4: la fórmula

`(núcleos × 2) + discos efectivos`: con 12 núcleos son 25; con 1 núcleo, 3. Con 1 núcleo coincide con lo medido: de 2 a 5 conexiones
están en la meseta y por encima solo empeora. Con los 12 núcleos de la base, en una corrida tranquila la consulta de CPU siguió subiendo
hasta ~20-28 conexiones (103 → 461 peticiones/s), que es donde apunta la fórmula (25). En otra corrida, con otros contenedores trabajando en
la misma máquina, las cifras de `/cpu` con 12 núcleos fueron erráticas (29 a 56 peticiones/s): los "12 núcleos" de Docker Desktop se
comparten con todo lo demás, y por eso el criterio automático del taller se comprueba solo con la base limitada a un núcleo, donde el
resultado es estable. La fórmula es un punto de partida, no un resultado: se confirma midiendo, en una máquina que no esté ocupada.

## Paso 6: tres instancias con un pool de 15

Cuenta: 3 × 15 = 45 conexiones pedidas; PostgreSQL admite 30, de las cuales 3 son reservadas a superusuarios (la aplicación no lo es): **27**.
Dos instancias se llevan 15 + 12 y la tercera se queda sin hueco. El error que se copia (de `resultados/tres-15-3.log`):

    Request processing failed: org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection
    Caused by: org.postgresql.util.PSQLException: FATAL: remaining connection slots are reserved for roles with the SUPERUSER attribute

**¿De quién es?** De PostgreSQL: el `FATAL` lo emite el servidor al rechazar la conexión; HikariCP solo lo propaga (en esa instancia el pool ni
siquiera llegó a inicializarse: `Exception during pool initialization`). No es un `Connection is not available` de HikariCP, que habría
significado que el pool existe pero no tiene conexiones libres a tiempo. La corrección sale de la cuenta: 27 − 3 de reserva para
administración = 24; con una cuarta instancia durante el despliegue gradual, 24 / 4 = **6 por instancia**. Con `config-final.yaml`
(3 + 1 instancias × 6 = 24 conexiones) no hay errores y se atienden unas 800 peticiones/s en total.

## Paso 7: la base se cae durante la carga (35 s)

| `connection-timeout` | Hilos de Tomcat ocupados | Cuánto tarda en fallar una petición |
|---|---|---|
| 30 s | 51 (los 50 usuarios, todos esperando una conexión) | ~30 000 ms |
| 2 s | 51 al principio; se liberan cada 2 s | ~2 000 ms |

Con 30 s, cada petición retiene un hilo de la aplicación durante medio minuto y los 50 usuarios quedan colgados: un servicio con 200
hilos se quedaría sin ellos en segundos y dejaría de responder hasta a lo que no necesita la base. Con 2 s fallan rápido, liberan el hilo y
el balanceador puede reaccionar. Un `connectionTimeout` corto no arregla la caída, pero acota el daño.

## Paso 8: sesiones ociosas que la base cierra al minuto

Con `idle_session_timeout = 60s` en PostgreSQL y 75 s sin tráfico, el usuario **no ve ningún error** (12 de 12 peticiones con 200): HikariCP
valida la conexión al prestarla (`validationTimeout`), descubre que está cerrada, la descarta y abre otra. Lo que se ve es el registro
(`Failed to validate connection ... (This connection has been closed.). Possibly consider using a shorter maxLifetime value`, 10 veces) y
la primera petición algo más lenta. La causa de fondo es que el pool no sabía que la conexión estaba muerta: con `keepalive-time` en 30 s
(por debajo de los 60 s de la base) el pool la mantiene viva y no hay ningún aviso. Con `max-lifetime` de 30 min y una base o proxy que
corta antes, el ajuste correcto es poner `maxLifetime` y `keepaliveTime` por debajo del corte más corto de la cadena.

## Paso 9: `minimum-idle` en 1 y un pico

(El enunciado pide cinco minutos sin tráfico; para que cupiera en la prueba se bajaron `idle-timeout` a 10 s, el mínimo que HikariCP acepta, y
el vigilante a 2 s con `com.zaxxer.hikari.housekeeping.periodMs`. El mecanismo es el mismo.)

| Pool | Conexiones antes del pico | p95 del pico |
|---|---|---|
| `minimum-idle` 1 | 1 | ~160 ms |
| tamaño fijo (10) | 10 | ~110 ms |

El pool variable se vació hasta una conexión y el pico tuvo que abrir nueve de golpe: más latencia justo cuando llega la carga. El tamaño fijo
tiene todas listas. Cuesta conexiones ociosas en la base, que se pagan de todos modos en la cuenta de capacidad máxima.

## Paso 10: la configuración final

Está en `config-final.yaml`, con un comentario junto a cada valor que dice de qué cuenta o medición sale: pool de 6 (cuenta del paso 6),
`connection-timeout` de 2 s (paso 7), `keepalive-time` de 30 s (paso 8), tamaño fijo (paso 9). Medida con una instancia y 50 usuarios, atiende
unas 280 peticiones/s con p95 ~190 ms y 0 errores (el pool de 6 es más pequeño que el de 10 de los pasos 1 a 3; la cuenta se hizo para
tres instancias y no para una).
