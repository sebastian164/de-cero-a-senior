# 05 — Redis como caché

> Requiere la lección [NoSQL y caché](../../java/nivel-08-bases-de-datos/06-nosql-y-cache/) (patrones de caché, estampida, penetración) y el módulo de [arquitectura hexagonal](../../hexagonal/) hasta la sesión [06](../../hexagonal/06-adaptadores-y-composicion/). El ejemplo se compila junto con el núcleo del banco, sin modificarlo.

## Idea

La pizarra del menú en un restaurante. El mesero no va a la cocina a preguntar cada plato: mira la pizarra. Funciona mientras alguien la mantenga. Si el chef cambia un plato y nadie borra la pizarra, los clientes piden lo que ya no hay, y si la pizarra no tiene fecha, eso dura para siempre. Si a mediodía se borra entera y entran cincuenta clientes a la vez, salen cincuenta meseros hacia la cocina. Y si alguien pide un plato que nunca existió, el mesero va a la cocina cada vez, porque en la pizarra no pone "eso no lo tenemos".

Las cuatro situaciones de esta sesión son esas cuatro: el dato viejo, la caducidad, la estampida y la penetración.

## Lo esencial

### Redis en tres frases

Redis guarda estructuras de datos **en memoria** y ejecuta las órdenes **de una en una**: cada orden es atómica, y entre dos órdenes de un cliente puede colarse otra. Puede persistir en disco (instantáneas o un registro de escrituras), pero el entorno del curso lo tiene apagado: aquí, si Redis se reinicia, todo desaparece, que es lo que debe poder soportar una caché. Y tiene un límite de memoria con una **política de desalojo**: qué borra cuando se llena.

| `maxmemory-policy` | Qué hace al llenarse | Para qué |
|---|---|---|
| `noeviction` (la del entorno) | Rechaza las escrituras | Datos que no se pueden perder por desalojo (sesión 06) |
| `allkeys-lru` / `allkeys-lfu` | Borra las claves menos usadas, sean cuales sean | Una instancia dedicada solo a caché |
| `volatile-lru` / `volatile-ttl`… | Borra solo claves con caducidad | Mezclar, con cuidado, caché y datos sin caducidad |

Mezclar en la misma instancia una caché con `allkeys-lru` y las claves de idempotencia de la sesión 06 es la forma de que un día de mucho tráfico se desalojen las claves que impedían cobrar dos veces.

### Las estructuras

| Estructura | Órdenes | Para qué en el banco |
|---|---|---|
| Cadena | `SET` con `EX`, `GET`, `INCR`, `SET … NX` | La vista de una cuenta en caché; contadores; cerrojos (sesión 06) |
| Hash | `HSET`, `HGET`, `HINCRBY`, `HGETALL` | Un objeto con campos; las estadísticas de la caché |
| Lista | `LPUSH`, `LRANGE`, `LTRIM` | Los últimos avisos de una cuenta |
| Conjunto | `SADD`, `SISMEMBER` | Las cuentas bloqueadas |
| Conjunto ordenado | `ZADD`, `ZRANGE`, `ZREMRANGEBYSCORE` | Un ranking; una ventana de tiempo (sesión 06) |

Con el entorno levantado, `docker compose -p nosql exec redis redis-cli --no-raw`:

```text
> SET banco:cuenta:v1:cuenta-1 "105000000|Ana" EX 300
OK
> GET banco:cuenta:v1:cuenta-1
"105000000|Ana"
> TTL banco:cuenta:v1:cuenta-1
(integer) 300
> INCR visitas:2026-11-15
(integer) 1
> HINCRBY banco:cache:estadisticas aciertos 1
(integer) 1
> HGETALL banco:cache:estadisticas
1) "aciertos"
2) "1"
> LPUSH avisos:cuenta-1 "deposito 50000" "transferencia 100000"
(integer) 2
> LRANGE avisos:cuenta-1 0 -1
1) "transferencia 100000"
2) "deposito 50000"
> SADD cuentas:bloqueadas cuenta-9 cuenta-12
(integer) 2
> SISMEMBER cuentas:bloqueadas cuenta-9
(integer) 1
> ZADD ranking:saldos 105000000 cuenta-1 20000000 cuenta-2 950000000 cuenta-3
(integer) 3
> ZREVRANGE ranking:saldos 0 1 WITHSCORES
1) "cuenta-3"
2) "950000000"
3) "cuenta-1"
4) "105000000"
> SET cerrojo:cierre ficha-a NX PX 1000
OK
> SET cerrojo:cierre ficha-b NX PX 1000
(nil)
```

El último `SET … NX` devuelve `(nil)`: la clave ya existía y no se escribió. Esa respuesta es la base de la sesión 06.

### Dónde va la caché en un servicio hexagonal

| Dónde | Qué guarda | Veredicto |
|---|---|---|
| Delante de un puerto de entrada de consulta (`ConsultarCuenta`) | Vistas para mostrar (`VistaDeCuenta`) | Aquí. Es un decorador: quien llama ve el mismo puerto |
| Delante de un puerto de salida (`RepositorioDeCuentas`) | Agregados con los que el dominio decide | No. Una cuenta vieja en caché trae una versión vieja: conflictos en cada escritura, o decisiones tomadas con un saldo que ya no es |
| Dentro del dominio o del caso de uso | — | No. El núcleo no debe saber que existe Redis |

### Las decisiones

- **Cuánto vive.** Siempre hay caducidad: es la red de seguridad de todo lo demás. Con un poco de **azar**, para que las claves cargadas a la vez no caduquen a la vez.
- **Cómo se invalida.** Quien cambia una cuenta **borra** su clave **después de confirmar**. Borrar y no actualizar: dos escritores que actualizan la caché pueden hacerlo en distinto orden que en la base, y gana el viejo. Después de confirmar: si se borra antes, un lector puede recargar el valor anterior antes de la confirmación.
- **Lo que no se invalida.** Queda una carrera: un lector que cargó el saldo viejo justo antes de la escritura y lo guarda justo después del borrado. También quedan los cambios que no pasan por el decorador (otro servicio, un proceso por lotes). Para eso, la caducidad; o eventos que borren claves (la bandeja de salida de la [sesión 04](../04-mongodb-en-el-banco-hexagonal/)).
- **Estampida.** Cuando una clave falta y llegan muchas peticiones, solo una carga; las demás esperan su resultado. Dentro de un proceso, con un mapa de cargas en curso. Entre varias instancias, cada una carga una vez; si eso sigue siendo mucho, un cerrojo en Redis para la recarga (sesión 06) o refrescar la clave antes de que caduque.
- **Penetración.** El "no existe" también se guarda, con una vida corta. Si el espacio de claves inventadas es enorme (un ataque que recorre identificadores), un filtro de Bloom: Redis 8 los trae (`BF.ADD`, `BF.EXISTS`).
- **Las claves.** Con dueño y versión del formato: `banco:cuenta:v1:{id}`. Si el formato cambia, cambia el prefijo, y las claves viejas mueren por caducidad.
- **Medir.** Aciertos y fallos. Una caché con un 20 % de aciertos es una base de datos más que operar.

## Ejemplo

El banco del módulo hexagonal con su almacén en memoria, envuelto para que cada lectura de una cuenta tarde 50 ms y quede contada, como una base de datos ocupada:

<!-- file: ejemplo/antes/src/BaseLenta.java -->
```java
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** El repositorio del banco, con la latencia de una base de datos ocupada: 50 ms por lectura. Cuenta las lecturas. */
final class BaseLenta implements RepositorioDeCuentas {

    private final RepositorioDeCuentas real;
    private final AtomicInteger lecturas = new AtomicInteger();

    BaseLenta(RepositorioDeCuentas real) {
        this.real = real;
    }

    @Override
    public String siguienteId() {
        return real.siguienteId();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        real.guardar(cuenta);
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        lecturas.incrementAndGet();
        try {
            Thread.sleep(50);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
        return real.buscar(id);
    }

    /** Devuelve las lecturas desde la última vez que se preguntó, y vuelve a empezar la cuenta. */
    int lecturasDesdeLaUltimaVez() {
        return lecturas.getAndSet(0);
    }
}
```

Los dos ejemplos ejecutan las mismas cuatro situaciones: cien consultas seguidas, un depósito, cien consultas de una cuenta que no existe y cincuenta consultas a la vez justo después de que la clave desaparece. Desde `ejemplo/antes` y desde `ejemplo/despues`:

```bash
mvn -q compile exec:java
```

### Antes

<!-- file: ejemplo/antes/src/banco/adaptadores/cache/CuentasConCache.java -->
```java
package banco.adaptadores.cache;

import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.puertos.entrada.ConsultarCuenta;
import banco.dominio.Movimiento;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.List;
import java.util.Optional;

/**
 * Cache-aside sobre la consulta de cuentas: mirar en Redis; si no está, preguntar al banco y guardar la respuesta.
 * Para quien llama es el mismo puerto ConsultarCuenta; el núcleo del banco no se entera.
 */
public final class CuentasConCache implements ConsultarCuenta {

    private final ConsultarCuenta banco;
    private final RedisCommands<String, String> redis;

    public CuentasConCache(ConsultarCuenta banco, RedisCommands<String, String> redis) {
        this.banco = banco;
        this.redis = redis;
    }

    @Override
    public Optional<VistaDeCuenta> porId(String cuentaId) {
        String guardada = redis.get(cuentaId);
        if (guardada != null) {
            return Optional.of(leer(cuentaId, guardada));
        }
        Optional<VistaDeCuenta> vista = banco.porId(cuentaId);
        vista.ifPresent(encontrada -> redis.set(cuentaId, escribir(encontrada)));   // "un saldo no caduca"
        return vista;
    }

    @Override
    public List<Movimiento> movimientosDe(String cuentaId) {
        return banco.movimientosDe(cuentaId);
    }

    /** El valor es "saldo|titular": el titular va al final porque puede llevar cualquier carácter. */
    private static String escribir(VistaDeCuenta vista) {
        return vista.saldoCentavos() + "|" + vista.titular();
    }

    private static VistaDeCuenta leer(String cuentaId, String valor) {
        String[] partes = valor.split("\\|", 2);
        return new VistaDeCuenta(cuentaId, partes[1], Long.parseLong(partes[0]));
    }
}
```

<!-- file: ejemplo/antes/src/Main.java -->
```java
import banco.adaptadores.cache.CuentasConCache;
import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.entrada.ConsultarCuenta;
import banco.aplicacion.puertos.entrada.Depositar;
import banco.aplicacion.puertos.salida.Reloj;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** El banco con una caché delante de la consulta de cuentas. Las consultas repetidas vuelan. */
public class Main {

    private static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");
    private static final Locale ES = Locale.forLanguageTag("es-CO");

    public static void main(String[] args) {
        RedisClient cliente = RedisClient.create("redis://localhost:6379");
        try (StatefulRedisConnection<String, String> conexion = cliente.connect()) {
            RedisCommands<String, String> redis = conexion.sync();
            redis.flushdb();                                     // solo porque es el Redis del taller

            AlmacenEnMemoria almacen = new AlmacenEnMemoria();
            BaseLenta base = new BaseLenta(almacen);
            ServicioDeCuentas servicio = new ServicioDeCuentas(base, almacen, almacen, almacen, RELOJ, almacen);
            ConsultarCuenta consultas = new CuentasConCache(servicio, redis);
            Depositar depositos = servicio;                      // los depósitos van directo al banco
            String ana = servicio.abrir("Ana", 100_000_000).id();
            base.lecturasDesdeLaUltimaVez();

            System.out.println("1. Ana consulta su saldo 100 veces");
            for (int i = 0; i < 100; i++) {
                consultas.porId(ana);
            }
            System.out.printf(ES, "   lecturas a la base: %d de 100 consultas%n", base.lecturasDesdeLaUltimaVez());

            System.out.println("2. Ana deposita 50.000 pesos y vuelve a consultar");
            depositos.depositar(ana, 5_000_000);
            System.out.printf(ES, "   la caché dice %,d pesos; el banco tiene %,d; la clave caduca en: %d (-1 es nunca)%n",
                    consultas.porId(ana).orElseThrow().saldoCentavos() / 100,
                    servicio.porId(ana).orElseThrow().saldoCentavos() / 100, redis.ttl(ana));
            base.lecturasDesdeLaUltimaVez();

            System.out.println("3. 100 consultas de una cuenta que no existe");
            for (int i = 0; i < 100; i++) {
                consultas.porId("cuenta-inventada");
            }
            System.out.printf(ES, "   lecturas a la base: %d%n", base.lecturasDesdeLaUltimaVez());

            System.out.println("4. La clave de Ana desaparece (se desalojó o Redis se reinició) y llegan 50 consultas a la vez");
            redis.del(ana);
            aLaVez(50, () -> consultas.porId(ana));
            System.out.printf(ES, "   lecturas a la base: %d%n", base.lecturasDesdeLaUltimaVez());

            System.out.println("Claves en Redis: " + new TreeSet<>(redis.keys("*")));
        } finally {
            cliente.shutdown();
        }
    }

    private static void aLaVez(int peticiones, Runnable consulta) {
        List<Future<?>> enviadas = new ArrayList<>();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(peticiones)) {
            for (int i = 0; i < peticiones; i++) {
                enviadas.add(ejecutor.submit(consulta));
            }
        }
        for (Future<?> enviada : enviadas) {
            try {
                enviada.get();
            } catch (InterruptedException | ExecutionException fallo) {
                throw new IllegalStateException(fallo);
            }
        }
    }
}
```

```text
1. Ana consulta su saldo 100 veces
   lecturas a la base: 1 de 100 consultas
2. Ana deposita 50.000 pesos y vuelve a consultar
   la caché dice 1.000.000 pesos; el banco tiene 1.050.000; la clave caduca en: -1 (-1 es nunca)
3. 100 consultas de una cuenta que no existe
   lecturas a la base: 100
4. La clave de Ana desaparece (se desalojó o Redis se reinició) y llegan 50 consultas a la vez
   lecturas a la base: 50
Claves en Redis: [cuenta-1]
```

### Análisis

| Situación | Lo que pasó |
|---|---|
| 1. Consultas repetidas | Funciona: una lectura a la base para cien consultas. Es lo único que se probó |
| 2. Depósito | Ana ve 1.000.000 y tiene 1.050.000. La clave no caduca nunca (`-1`): sin invalidación y sin caducidad, el error no se corrige solo. Ana llamará al banco |
| 3. Cuenta inexistente | Cien lecturas a la base. Lo que no existe no se guarda, así que cada consulta llega al fondo. Un cliente con un error, o alguien recorriendo identificadores, le pega directamente a la base |
| 4. La clave desaparece | Cincuenta lecturas a la vez. Con una consulta pesada y miles de usuarios, es la estampida que tumba la base justo después de reiniciar Redis o de un despliegue |
| La clave | `cuenta-1`, sin dueño ni versión. En un Redis compartido, otro servicio puede usar el mismo nombre |

### Después

Un solo decorador para los tres puertos que tocan cuentas: consulta (con caché), depósito y transferencia (que invalidan).

<!-- file: ejemplo/despues/src/banco/adaptadores/cache/CuentasConCache.java -->
```java
package banco.adaptadores.cache;

import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.puertos.entrada.ConsultarCuenta;
import banco.aplicacion.puertos.entrada.Depositar;
import banco.aplicacion.puertos.entrada.TransferirDinero;
import banco.dominio.Movimiento;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cache-aside sobre la consulta de cuentas, con las cuatro decisiones tomadas:
 *   cuánto vive      -> 5 minutos, más un azar de hasta 1 minuto para que no caduquen todas a la vez
 *   cómo se invalida -> quien cambia una cuenta a través de este adaptador borra su clave, después de confirmar
 *   qué pasa si no existe -> se guarda también el "no existe", con una vida corta
 *   qué pasa si muchos fallan a la vez -> en este proceso, solo uno va al banco y los demás esperan su respuesta
 * Decora los puertos de entrada: quien llama ve el mismo banco, y el núcleo no se entera de que hay Redis.
 */
public final class CuentasConCache implements ConsultarCuenta, Depositar, TransferirDinero {

    private static final String PREFIJO = "banco:cuenta:v1:";           // de quién es la clave y con qué formato
    private static final String ESTADISTICAS = "banco:cache:estadisticas";
    private static final String NO_EXISTE = "-";
    private static final long VIDA_SEGUNDOS = 300;
    private static final long AZAR_SEGUNDOS = 60;
    private static final long VIDA_NO_EXISTE_SEGUNDOS = 30;

    private final ConsultarCuenta consultas;
    private final Depositar depositos;
    private final TransferirDinero transferencias;
    private final RedisCommands<String, String> redis;
    private final ConcurrentHashMap<String, CompletableFuture<Optional<VistaDeCuenta>>> enCurso = new ConcurrentHashMap<>();

    public CuentasConCache(ConsultarCuenta consultas, Depositar depositos, TransferirDinero transferencias,
                           RedisCommands<String, String> redis) {
        this.consultas = consultas;
        this.depositos = depositos;
        this.transferencias = transferencias;
        this.redis = redis;
    }

    @Override
    public Optional<VistaDeCuenta> porId(String cuentaId) {
        String guardada = redis.get(PREFIJO + cuentaId);
        if (guardada != null) {
            redis.hincrby(ESTADISTICAS, "aciertos", 1);
            return leer(cuentaId, guardada);
        }
        redis.hincrby(ESTADISTICAS, "fallos", 1);
        return cargarUnaSolaVez(cuentaId);
    }

    /** Si ya hay una carga de esta cuenta en curso, se espera su resultado en lugar de ir otra vez al banco. */
    private Optional<VistaDeCuenta> cargarUnaSolaVez(String cuentaId) {
        CompletableFuture<Optional<VistaDeCuenta>> mia = new CompletableFuture<>();
        CompletableFuture<Optional<VistaDeCuenta>> deOtro = enCurso.putIfAbsent(cuentaId, mia);
        if (deOtro != null) {
            return deOtro.join();
        }
        try {
            String guardada = redis.get(PREFIJO + cuentaId);    // otro pudo terminar justo antes de que llegáramos
            Optional<VistaDeCuenta> vista = guardada != null ? leer(cuentaId, guardada) : cargar(cuentaId);
            mia.complete(vista);
            return vista;
        } catch (RuntimeException error) {
            mia.completeExceptionally(error);
            throw error;
        } finally {
            enCurso.remove(cuentaId, mia);
        }
    }

    private Optional<VistaDeCuenta> cargar(String cuentaId) {
        Optional<VistaDeCuenta> vista = consultas.porId(cuentaId);
        if (vista.isPresent()) {
            long vida = VIDA_SEGUNDOS + ThreadLocalRandom.current().nextLong(AZAR_SEGUNDOS);
            redis.set(PREFIJO + cuentaId, escribir(vista.get()), SetArgs.Builder.ex(vida));
        } else {
            redis.set(PREFIJO + cuentaId, NO_EXISTE, SetArgs.Builder.ex(VIDA_NO_EXISTE_SEGUNDOS));
        }
        return vista;
    }

    @Override
    public List<Movimiento> movimientosDe(String cuentaId) {
        return consultas.movimientosDe(cuentaId);                // el extracto no se guarda en caché: cambia con cada movimiento
    }

    /** El caso de uso confirma su transacción y vuelve; solo entonces se borra la copia. Borrar, no actualizar. */
    @Override
    public VistaDeCuenta depositar(String cuentaId, long centavos) {
        VistaDeCuenta resultado = depositos.depositar(cuentaId, centavos);
        redis.del(PREFIJO + cuentaId);
        return resultado;
    }

    @Override
    public Resultado transferir(Orden orden) {
        Resultado resultado = transferencias.transferir(orden);
        redis.del(PREFIJO + orden.origenId(), PREFIJO + orden.destinoId());
        return resultado;
    }

    private static String escribir(VistaDeCuenta vista) {
        return vista.saldoCentavos() + "|" + vista.titular();
    }

    private static Optional<VistaDeCuenta> leer(String cuentaId, String valor) {
        if (valor.equals(NO_EXISTE)) {
            return Optional.empty();
        }
        String[] partes = valor.split("\\|", 2);
        return Optional.of(new VistaDeCuenta(cuentaId, partes[1], Long.parseLong(partes[0])));
    }
}
```

<!-- file: ejemplo/despues/src/Main.java -->
```java
import banco.adaptadores.cache.CuentasConCache;
import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.salida.Reloj;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** El mismo banco y las mismas cuatro situaciones, con la caché bien decidida. */
public class Main {

    private static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");
    private static final Locale ES = Locale.forLanguageTag("es-CO");

    public static void main(String[] args) {
        RedisClient cliente = RedisClient.create("redis://localhost:6379");
        try (StatefulRedisConnection<String, String> conexion = cliente.connect()) {
            RedisCommands<String, String> redis = conexion.sync();
            redis.flushdb();                                     // solo porque es el Redis del taller

            AlmacenEnMemoria almacen = new AlmacenEnMemoria();
            BaseLenta base = new BaseLenta(almacen);
            ServicioDeCuentas servicio = new ServicioDeCuentas(base, almacen, almacen, almacen, RELOJ, almacen);
            CuentasConCache banco = new CuentasConCache(servicio, servicio, servicio, redis);   // lo que ven los adaptadores de entrada
            String ana = servicio.abrir("Ana", 100_000_000).id();
            String luis = servicio.abrir("Luis", 0).id();
            String clave = "banco:cuenta:v1:" + ana;
            base.lecturasDesdeLaUltimaVez();

            System.out.println("1. Ana consulta su saldo 100 veces");
            for (int i = 0; i < 100; i++) {
                banco.porId(ana);
            }
            System.out.printf(ES, "   lecturas a la base: %d de 100 consultas%n", base.lecturasDesdeLaUltimaVez());

            System.out.println("2. Ana deposita 50.000 pesos y vuelve a consultar");
            banco.depositar(ana, 5_000_000);
            System.out.printf(ES, "   la caché dice %,d pesos; el banco tiene %,d; la clave caduca en: %d s%n",
                    banco.porId(ana).orElseThrow().saldoCentavos() / 100,
                    servicio.porId(ana).orElseThrow().saldoCentavos() / 100, redis.ttl(clave));
            banco.transferir(new Orden("orden-1", ana, luis, 10_000_000));
            System.out.printf(ES, "   tras transferir 100.000 a Luis: Ana %,d y Luis %,d, según la caché%n",
                    banco.porId(ana).orElseThrow().saldoCentavos() / 100, banco.porId(luis).orElseThrow().saldoCentavos() / 100);
            base.lecturasDesdeLaUltimaVez();

            System.out.println("3. 100 consultas de una cuenta que no existe");
            for (int i = 0; i < 100; i++) {
                banco.porId("cuenta-inventada");
            }
            System.out.printf(ES, "   lecturas a la base: %d; el \"no existe\" caduca en %d s%n",
                    base.lecturasDesdeLaUltimaVez(), redis.ttl("banco:cuenta:v1:cuenta-inventada"));

            System.out.println("4. La clave de Ana caduca y llegan 50 consultas a la vez");
            redis.del(clave);
            aLaVez(50, () -> banco.porId(ana));
            System.out.printf(ES, "   lecturas a la base: %d%n", base.lecturasDesdeLaUltimaVez());

            System.out.println("Claves en Redis: " + new TreeSet<>(redis.keys("*")));
            System.out.println("Estadísticas: " + new TreeMap<>(redis.hgetall("banco:cache:estadisticas")));
        } finally {
            cliente.shutdown();
        }
    }

    private static void aLaVez(int peticiones, Runnable consulta) {
        List<Future<?>> enviadas = new ArrayList<>();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(peticiones)) {
            for (int i = 0; i < peticiones; i++) {
                enviadas.add(ejecutor.submit(consulta));
            }
        }
        for (Future<?> enviada : enviadas) {
            try {
                enviada.get();
            } catch (InterruptedException | ExecutionException fallo) {
                throw new IllegalStateException(fallo);
            }
        }
    }
}
```

```text
1. Ana consulta su saldo 100 veces
   lecturas a la base: 1 de 100 consultas
2. Ana deposita 50.000 pesos y vuelve a consultar
   la caché dice 1.050.000 pesos; el banco tiene 1.050.000; la clave caduca en: 351 s
   tras transferir 100.000 a Luis: Ana 950.000 y Luis 100.000, según la caché
3. 100 consultas de una cuenta que no existe
   lecturas a la base: 1; el "no existe" caduca en 24 s
4. La clave de Ana caduca y llegan 50 consultas a la vez
   lecturas a la base: 1
Claves en Redis: [banco:cache:estadisticas, banco:cuenta:v1:cuenta-1, banco:cuenta:v1:cuenta-2, banco:cuenta:v1:cuenta-inventada]
Estadísticas: {aciertos=198, fallos=55}
```

| Situación | Por qué ahora va bien |
|---|---|
| 2. Depósito y transferencia | El decorador borra la clave cuando el caso de uso vuelve, es decir, después de confirmar. La consulta siguiente recarga. La clave vive entre 300 y 360 segundos |
| 3. Cuenta inexistente | Una lectura. El "no existe" se guarda 30 segundos. Si la cuenta se abre en ese tiempo, tardará hasta 30 segundos en verse: es el precio, y por eso esa vida es corta (o se borra la clave negativa al abrir) |
| 4. La clave caduca | Una lectura: la primera petición carga y las otras 49 esperan su resultado. Con dos instancias del servicio serían dos |
| Estadísticas | 198 aciertos y 55 fallos: un 78 %. Los 50 de la estampida cuentan como fallos aunque solo uno fue a la base |

El núcleo del banco no cambió. La raíz de composición entrega `CuentasConCache` a los adaptadores de entrada en lugar de `ServicioDeCuentas`; los dos implementan los mismos puertos.

## Errores comunes

- Claves sin caducidad.
- Actualizar la caché al escribir en lugar de borrarla.
- Borrar antes de confirmar la transacción.
- Cachear el agregado que el dominio usa para decidir, en lugar de la vista que se muestra.
- No guardar el "no existe", o guardarlo con la misma vida que lo que sí existe.
- Todas las claves con la misma vida, cargadas a la vez tras un despliegue.
- Claves sin prefijo ni versión en un Redis compartido.
- Un tiempo de espera del cliente de Redis por defecto (en Lettuce, 60 segundos): si Redis se cuelga, el servicio se cuelga con él. Una caché caída debe degradar a "ir a la base", no a "no responder".
- No medir la tasa de aciertos.
- Mezclar caché y datos críticos en una instancia con desalojo de cualquier clave.

## Taller

**Objetivo:** poner una caché delante del catálogo de tu servicio de pedidos, demostrar cada una de sus decisiones con una medición y que el servicio sobreviva sin Redis.

Sobre tu servicio de pedidos del [módulo hexagonal](../../hexagonal/), con Lettuce y sin Spring.

1. Escribe un decorador de tu puerto de consulta del catálogo (precio y existencias de un producto) con cache-aside, caducidad con azar y claves con prefijo y versión. Mide la tasa de aciertos y la latencia con una carga de lectura.
2. Haz que el cambio de precio, por tu puerto de entrada, borre la clave después de confirmar. Luego cambia un precio con un proceso que no pase por el decorador (directo en la base) y mide cuánto tarda en verse. Propón una invalidación por eventos con tu bandeja de salida y mide de nuevo.
3. Provoca una estampida: 200 peticiones a la vez sobre una clave recién caducada, con una lectura de la base de 100 ms. Cuenta las lecturas. Añade la carga única por proceso y repite. Levanta tres instancias y repite. Si tres te parece mucho, implementa una de las alternativas (cerrojo de recarga o refresco anticipado) y compara.
4. Provoca penetración con identificadores al azar. Añade la caché negativa. Después, un filtro de Bloom con los identificadores existentes, y compara memoria (`MEMORY USAGE`) y lecturas a la base.
5. Detén Redis (`docker compose -p nosql stop redis`) con el servicio bajo carga. Sin cambios, ¿qué pasa con la latencia? Configura los tiempos de espera del cliente y haz que el decorador vaya a la base si Redis falla. Repite.
6. Con `maxmemory 5mb` y `allkeys-lru`, llena la caché y observa `evicted_keys` en `INFO stats` y la tasa de aciertos.
7. Para convencerte de dónde no va la caché: pon una delante de tu repositorio de pedidos y muestra el problema con una escritura concurrente.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Ninguna clave sin caducidad | `TTL` sobre una muestra de claves |
| El tiempo máximo que se puede ver un dato viejo está medido y escrito | Paso 2 |
| La estampida carga una vez por instancia, o menos | Paso 3 |
| Lo inexistente no llega a la base en cada consulta | Paso 4 |
| Con Redis caído, el servicio responde, más lento | Paso 5 |
| El núcleo del servicio no conoce Redis | Búsqueda de `io.lettuce` fuera del adaptador |

**Entrega:** rama `taller/nosql-05`, con el código, las mediciones y `ANALISIS.md` en `taller/solucion/`.

## En el trabajo real

Con Spring, esta sesión es `@Cacheable` sobre el servicio de consulta con un `RedisCacheManager`: caducidad por caché (`entryTtl`), prefijo de claves y serialización en JSON en lugar de la serialización de Java. Tres detalles que la anotación no decide por ti. `@Cacheable(sync = true)` pide que un solo hilo cargue cada clave, pero con el `RedisCacheManager` por defecto no lo consigue (200 peticiones desde 50 hilos hicieron 50 cargas): la carga única exige un escritor con cerrojo, `RedisCacheWriter.lockingRedisCacheWriter`. `@CacheEvict` dentro de un método `@Transactional` borra **antes** de confirmar o después según el orden de los proxies: con la transacción por fuera (`@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)`) borra antes, salvo que el gestor de caché sea consciente de las transacciones (`transactionAware()`), que lo aplaza; con el orden por defecto de Spring Boot borró después de confirmar. Y el decorador del ejemplo es lo que hace el proxy de Spring, con la misma limitación de la lección del Nivel 8: las llamadas internas no pasan por él.

La pregunta de diseño que precede a todo esto es si hace falta la caché. Una consulta lenta por falta de índice se arregla con el índice ([sesión 02](../02-indices-y-explain/)), no con una copia que hay que mantener.

## Preguntas senior

1. ¿Por qué en una arquitectura hexagonal la caché decora un puerto de consulta y no el repositorio del agregado?
2. ¿Por qué se borra la clave en lugar de actualizarla, y por qué después de confirmar?
3. ¿Qué carrera queda aunque invalides bien, y qué la acota?
4. ¿Cómo evitas una estampida en un proceso? ¿Y con diez instancias?
5. ¿Qué vida le das al "no existe" y qué cuesta equivocarse en un sentido o en el otro?
6. Redis se cae. ¿Qué hace tu servicio y cómo lo has comprobado?
7. ¿Qué política de desalojo usarías para una instancia de caché, y por qué no la usarías para claves de idempotencia?
