# 06 — Redis más allá de la caché

> Requiere la [sesión 05](../05-redis-como-cache/), la [sesión 03](../03-consistencia-en-mongodb/) (actualización condicional) y, para el contexto de idempotencia, la sesión de [garantías de entrega e idempotencia](../../kafka-y-sagas/kafka/04-entrega-e-idempotencia/) de Kafka.

## Idea

La llave del baño de una estación de servicio, colgada de una tabla de madera. Quien la tiene, entra; al salir, la devuelve. Para que nadie se la lleve, el encargado tiene una copia y, si alguien tarda más de diez minutos, se la da al siguiente. Funciona hasta que el primero, que solo se había quedado dormido, despierta y entra con su llave mientras el segundo está dentro.

La solución no es una llave mejor. Es que cada llave lleve un número que crece, y que la puerta no abra con un número menor que el último que vio. La puerta, no la llave, es la que garantiza que no entren dos.

## Lo esencial

### Por qué Redis sirve para esto, y por qué con cuidado

Sirve porque cada orden es atómica, porque `SET … NX` crea una clave solo si no existe, porque las claves pueden caducar solas y porque un **script de Lua se ejecuta entero sin que se cuele ninguna otra orden**. Y porque todas las instancias de un servicio ven el mismo Redis.

Hay que tener cuidado porque Redis no es la fuente de verdad. Su réplica es asíncrona: si el primario cae, el nuevo puede no tener las últimas escrituras. Sin persistencia, un reinicio lo borra todo. Con una política de desalojo, puede borrar claves por falta de memoria. Cualquier uso de esta sesión tiene que responder qué pasa si la clave desaparece.

### Idempotencia en el borde

El cliente manda una clave por petición (la cabecera `Idempotency-Key`) y la repite si reintenta. El adaptador HTTP:

1. **Reserva** la clave de forma atómica: `SET pago:{clave} EN_CURSO NX EX 30`. Solo una petición lo consigue.
2. Hace el trabajo y **guarda el resultado** en la misma clave, con una vida igual a lo que el cliente puede tardar en reintentar (24 horas es habitual).
3. Una repetición recibe **el mismo resultado**; si la primera sigue en curso, un `409` para que reintente; si llega la misma clave con otro contenido, un `422`: es un error del cliente, no una repetición.

Esto no sustituye a `OrdenesProcesadas` del banco, que registra la orden **dentro de la transacción** de la base. Son dos cosas distintas:

| | Clave en la transacción (banco) | Clave en Redis (borde) |
|---|---|---|
| Protege | Los datos de la propia base | Lo que la transacción no cubre: un servicio externo, trabajo caro, la respuesta |
| Garantía | Atómica con el cambio: o las dos cosas o ninguna | Separada del trabajo: hay un instante entre hacerlo y apuntarlo |
| Si se pierde la clave | No se pierde: está en la base | Un reintento se procesa otra vez |

El hueco de la tercera fila tiene una sola solución completa: que el servicio externo también sea idempotente y reciba la misma clave. Las pasarelas de pago serias aceptan una.

### Límites de tasa

| Algoritmo | Cómo | Precio |
|---|---|---|
| Ventana fija | `INCR` y caducidad de un segundo | Barato. Deja pasar el doble justo en el cambio de ventana |
| Ventana deslizante con registro | Un conjunto ordenado con el instante de cada petición | Exacto. Un elemento por petición aceptada |
| Cubo de fichas | Fichas que se reponen a ritmo fijo, con un máximo | Permite ráfagas controladas. Es el de muchas pasarelas de API |

Sea cual sea, contar y poner la caducidad tienen que ser **un solo paso**. `INCR` y después `EXPIRE` son dos órdenes: si el proceso cae entre las dos, la clave no caduca nunca y el cliente queda bloqueado para siempre. Un script de Lua lo resuelve, y además puede usar el reloj de Redis (`TIME`) en lugar del de cada instancia.

### Cerrojos entre instancias

Lo mínimo correcto:

- `SET cerrojo:{recurso} {ficha} NX PX {vida}`, con una **ficha** distinta para cada dueño.
- Soltarlo comparando la ficha y borrando, en un script: solo el dueño puede soltarlo.

Y lo que ningún cerrojo con caducidad resuelve: si el dueño se congela más de lo que dura el cerrojo (una pausa larga del recolector de basura, una máquina virtual suspendida, una red que se corta), el cerrojo caduca, otro lo toma y **hay dos dueños**. El primero no tiene forma de saberlo hasta que despierta, y para entonces ya está escribiendo.

La defensa es un **turno** (en inglés, *fencing token*): cada vez que alguien toma el cerrojo recibe un número mayor que el anterior, y el recurso protegido rechaza las escrituras con un número menor que el último que vio. Es la actualización condicional de la sesión 03 aplicada al turno. Exige que el recurso coopere; si no puede, el cerrojo no garantiza nada.

La regla práctica: un cerrojo en Redis está bien **para no hacer dos veces un trabajo** (que dos instancias no ejecuten la misma tarea nocturna). Para que **dos nunca escriban a la vez**, la garantía tiene que estar en el recurso: un turno, una restricción única, una versión.

## Ejemplo

Tres usos, cada uno en su clase, ejecutados en secuencia desde `Main`. El servicio externo que cobra tarda 100 ms y cuenta los cobros:

<!-- file: ejemplo/antes/src/Pasarela.java -->
```java
import java.util.concurrent.atomic.AtomicInteger;

/** El servicio externo que cobra: tarda 100 ms y no sabe de transacciones ni de repeticiones. Cuenta los cobros. */
final class Pasarela {

    private final AtomicInteger cobros = new AtomicInteger();

    String cobrar(String cuenta, long centavos) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
        return "aprobado-" + cobros.incrementAndGet();
    }

    int cobros() {
        return cobros.get();
    }
}
```

Desde `ejemplo/antes` y desde `ejemplo/despues`:

```bash
mvn -q compile exec:java
```

El ejemplo usa tiempos reales (pausas de 150 ms a 1,5 s) para que las claves caduquen de verdad.

### Antes

<!-- file: ejemplo/antes/src/Idempotencia.java -->
```java
import io.lettuce.core.api.sync.RedisCommands;

/** El borde HTTP de "pagar un servicio": la cabecera Idempotency-Key evita cobrar dos veces la misma petición. */
final class Idempotencia {

    private final RedisCommands<String, String> redis;
    private final Pasarela pasarela;

    Idempotencia(RedisCommands<String, String> redis, Pasarela pasarela) {
        this.redis = redis;
        this.pasarela = pasarela;
    }

    String pagar(String clave, String cuenta, long centavos) {
        String anterior = redis.get("pago:" + clave);            // ¿ya la atendimos?
        if (anterior != null) {
            return "200 " + anterior + " (repetida)";
        }
        String cobro = pasarela.cobrar(cuenta, centavos);        // entre el GET y el SET pasan 100 ms
        redis.set("pago:" + clave, cobro);                       // y se guarda para siempre
        return "201 " + cobro;
    }
}
```

<!-- file: ejemplo/antes/src/LimiteDeTasa.java -->
```java
import io.lettuce.core.api.sync.RedisCommands;

/** Como mucho 5 peticiones por segundo por cliente: un contador que vive un segundo desde la primera petición. */
final class LimiteDeTasa {

    private static final int LIMITE = 5;

    private final RedisCommands<String, String> redis;
    private boolean caerseTrasContar = false;

    LimiteDeTasa(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    boolean permitir(String cliente) {
        String clave = "tasa:" + cliente;
        long peticiones = redis.incr(clave);
        if (caerseTrasContar) {
            caerseTrasContar = false;
            throw new IllegalStateException("el proceso se reinició justo después de contar");
        }
        if (peticiones == 1) {
            redis.pexpire(clave, 1_000);                         // la ventana empieza con la primera petición
        }
        return peticiones <= LIMITE;
    }

    /** Para la demostración: la próxima llamada falla entre las dos órdenes. */
    void caerseEnLaProxima() {
        caerseTrasContar = true;
    }
}
```

<!-- file: ejemplo/antes/src/Cerrojo.java -->
```java
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;

/** Un cerrojo entre instancias: quien consigue crear la clave, trabaja; al terminar, la borra. Caduca por si alguien muere. */
final class Cerrojo {

    private final RedisCommands<String, String> redis;

    Cerrojo(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    boolean tomar(String recurso, long vidaMs) {
        return "OK".equals(redis.set("cerrojo:" + recurso, "ocupado", SetArgs.Builder.nx().px(vidaMs)));
    }

    void soltar(String recurso) {
        redis.del("cerrojo:" + recurso);
    }
}
```

<!-- file: ejemplo/antes/src/LibroMayor.java -->
```java
import java.util.ArrayList;
import java.util.List;

/** Lo que protege el cerrojo: el cierre del día. Si dos lo escriben a la vez, queda mal. Acepta cualquier escritura. */
final class LibroMayor {

    private final List<String> asientos = new ArrayList<>();

    synchronized boolean escribir(String quien, String asiento) {
        asientos.add(quien + ": " + asiento);
        return true;
    }

    synchronized List<String> asientos() {
        return List.copyOf(asientos);
    }
}
```

<!-- file: ejemplo/antes/src/Main.java -->
```java
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Tres usos de Redis que no son caché, cada uno escrito de la forma más directa. Con un usuario, todo funciona. */
public class Main {

    public static void main(String[] args) {
        RedisClient cliente = RedisClient.create("redis://localhost:6379");
        try (StatefulRedisConnection<String, String> conexion = cliente.connect()) {
            RedisCommands<String, String> redis = conexion.sync();
            redis.flushdb();                                     // solo porque es el Redis del taller
            idempotencia(redis);
            limiteDeTasa(redis);
            cerrojo(redis);
        } finally {
            cliente.shutdown();
        }
    }

    private static void idempotencia(RedisCommands<String, String> redis) {
        System.out.println("1. Idempotencia: el móvil no recibió respuesta y reenvió el mismo pago 5 veces");
        Pasarela pasarela = new Pasarela();
        Idempotencia pagos = new Idempotencia(redis, pasarela);
        List<String> respuestas = aLaVez(5, () -> pagos.pagar("pago-77", "cuenta-1", 8_000_000));
        System.out.println("   respuestas: " + respuestas);
        System.out.println("   cobros en la pasarela: " + pasarela.cobros());
        System.out.println("   un reintento al rato: " + pagos.pagar("pago-77", "cuenta-1", 8_000_000));
        System.out.println("   la clave caduca en: " + redis.ttl("pago:pago-77") + " (-1 es nunca)");
    }

    private static void limiteDeTasa(RedisCommands<String, String> redis) {
        System.out.println("2a. Límite de 5 por segundo: una ráfaga justo en el cambio de ventana");
        LimiteDeTasa limite = new LimiteDeTasa(redis);
        limite.permitir("cliente-1");                            // t = 0: empieza la ventana
        dormir(900);
        int aceptadas = contarAceptadas(limite, "cliente-1", 4); // t = 900 ms
        dormir(150);
        aceptadas += contarAceptadas(limite, "cliente-1", 5);    // t = 1.050 ms
        System.out.println("   entre t=900 ms y t=1.050 ms se aceptaron " + aceptadas + " de 9");

        System.out.println("2b. El proceso se cae entre contar y poner la caducidad");
        limite.caerseEnLaProxima();
        try {
            limite.permitir("cliente-2");
        } catch (IllegalStateException caida) {
            System.out.println("   " + caida.getMessage());
        }
        System.out.println("   6 peticiones: aceptadas " + contarAceptadas(limite, "cliente-2", 6));
        dormir(1_200);
        System.out.println("   1,2 s después, otra: " + (limite.permitir("cliente-2") ? "aceptada" : "rechazada")
                + "; la clave caduca en: " + redis.ttl("tasa:cliente-2") + " (-1 es nunca)");
    }

    private static void cerrojo(RedisCommands<String, String> redis) {
        System.out.println("3. Cerrojo para el cierre del día, con vida de 1 s");
        Cerrojo cerrojo = new Cerrojo(redis);
        LibroMayor libro = new LibroMayor();

        System.out.println("   A toma el cerrojo: " + siNo(cerrojo.tomar("cierre", 1_000)));
        dormir(1_500);                                           // A se congela: una pausa larga del recolector de basura
        System.out.println("   B toma el cerrojo: " + siNo(cerrojo.tomar("cierre", 1_000)));
        libro.escribir("B", "cierre del 15 de noviembre");
        System.out.println("   A despierta y escribe: " + (libro.escribir("A", "cierre del 15 de noviembre") ? "aceptado" : "rechazado"));
        cerrojo.soltar("cierre");                                // A suelta… el cerrojo de B
        System.out.println("   C toma el cerrojo mientras B sigue trabajando: " + siNo(cerrojo.tomar("cierre", 1_000)));
        System.out.println("   el libro: " + libro.asientos());
    }

    private static int contarAceptadas(LimiteDeTasa limite, String cliente, int peticiones) {
        int aceptadas = 0;
        for (int i = 0; i < peticiones; i++) {
            if (limite.permitir(cliente)) {
                aceptadas++;
            }
        }
        return aceptadas;
    }

    private static List<String> aLaVez(int veces, Callable<String> peticion) {
        List<Future<String>> enviadas = new ArrayList<>();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(veces)) {
            for (int i = 0; i < veces; i++) {
                enviadas.add(ejecutor.submit(peticion));
            }
        }
        List<String> respuestas = new ArrayList<>();
        for (Future<String> enviada : enviadas) {
            try {
                respuestas.add(enviada.get());
            } catch (InterruptedException | ExecutionException fallo) {
                throw new IllegalStateException(fallo);
            }
        }
        Collections.sort(respuestas);
        return respuestas;
    }

    private static String siNo(boolean valor) {
        return valor ? "sí" : "no";
    }

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }
}
```

```text
1. Idempotencia: el móvil no recibió respuesta y reenvió el mismo pago 5 veces
   respuestas: [201 aprobado-1, 201 aprobado-2, 201 aprobado-3, 201 aprobado-4, 201 aprobado-5]
   cobros en la pasarela: 5
   un reintento al rato: 200 aprobado-5 (repetida)
   la clave caduca en: -1 (-1 es nunca)
2a. Límite de 5 por segundo: una ráfaga justo en el cambio de ventana
   entre t=900 ms y t=1.050 ms se aceptaron 9 de 9
2b. El proceso se cae entre contar y poner la caducidad
   el proceso se reinició justo después de contar
   6 peticiones: aceptadas 4
   1,2 s después, otra: rechazada; la clave caduca en: -1 (-1 es nunca)
3. Cerrojo para el cierre del día, con vida de 1 s
   A toma el cerrojo: sí
   B toma el cerrojo: sí
   A despierta y escribe: aceptado
   C toma el cerrojo mientras B sigue trabajando: sí
   el libro: [B: cierre del 15 de noviembre, A: cierre del 15 de noviembre]
```

### Análisis

| Situación | Lo que pasó |
|---|---|
| 1. Idempotencia | Cinco cobros por un solo pago. Las cinco peticiones leyeron "no está" antes de que ninguna escribiera: entre el `GET` y el `SET` hay 100 ms de pasarela. Es la comprobación que envejece de la sesión 03. Además, la clave no caduca: cada pago deja una clave para siempre |
| 2a. Ventana fija | Nueve peticiones aceptadas en 150 ms con un límite de cinco por segundo: la ventana empezó a contar en la primera petición y se reinició a los 1.000 ms, en mitad de la ráfaga |
| 2b. Caída entre órdenes | El primer `INCR` dejó la clave a 1 sin caducidad; como el `PEXPIRE` solo se pone cuando el contador vale 1, ya no se pondrá nunca. Tras cuatro peticiones más, el cliente queda bloqueado para siempre. Lo arreglará alguien borrando la clave a mano en producción |
| 3. Cerrojo | A se congeló, su cerrojo caducó y B lo tomó: hasta ahí, es lo esperable. Pero A despertó, escribió el mismo cierre por segunda vez, y al soltar **borró el cerrojo de B**, así que C entró mientras B seguía trabajando |

### Después

<!-- file: ejemplo/despues/src/Idempotencia.java -->
```java
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * El borde HTTP de "pagar un servicio", con la clave de idempotencia reservada de forma atómica:
 *   SET pago:{clave} EN_CURSO NX EX 30  -> solo una petición la consigue; las demás saben que hay otra en curso
 *   al terminar, el resultado sustituye a la reserva y se guarda 24 horas, que es lo que el cliente puede tardar en reintentar
 * Se guarda también una huella de la petición: la misma clave con otro contenido es un error del cliente, no una repetición.
 */
final class Idempotencia {

    private static final long RESERVA_SEGUNDOS = 30;
    private static final long RESULTADO_SEGUNDOS = 24 * 60 * 60;

    private final RedisCommands<String, String> redis;
    private final Pasarela pasarela;

    Idempotencia(RedisCommands<String, String> redis, Pasarela pasarela) {
        this.redis = redis;
        this.pasarela = pasarela;
    }

    String pagar(String clave, String cuenta, long centavos) {
        String llave = "pago:" + clave;
        String huella = cuenta + ":" + centavos;
        String reservada = redis.set(llave, "EN_CURSO|" + huella, SetArgs.Builder.nx().ex(RESERVA_SEGUNDOS));
        if (!"OK".equals(reservada)) {
            return responderRepeticion(redis.get(llave), huella);
        }
        try {
            String cobro = pasarela.cobrar(cuenta, centavos);
            redis.set(llave, "HECHO|" + huella + "|" + cobro, SetArgs.Builder.ex(RESULTADO_SEGUNDOS));
            return "201 " + cobro;
        } catch (RuntimeException fallo) {
            redis.del(llave);                                    // no se cobró: que el cliente pueda reintentar
            throw fallo;
        }
    }

    private static String responderRepeticion(String guardado, String huella) {
        if (guardado == null) {
            return "409 en curso, reintenta";                    // la reserva caducó entre el SET y el GET
        }
        String[] partes = guardado.split("\\|", 3);
        if (!partes[1].equals(huella)) {
            return "422 la clave ya se usó con otra petición";
        }
        if (partes[0].equals("EN_CURSO")) {
            return "409 en curso, reintenta";
        }
        return "200 " + partes[2] + " (repetida)";
    }
}
```

<!-- file: ejemplo/despues/src/LimiteDeTasa.java -->
```java
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.UUID;

/**
 * Como mucho 5 peticiones en cualquier segundo, por cliente: una ventana deslizante en un conjunto ordenado.
 * Cada petición aceptada es un miembro cuya puntuación es su instante. Todo ocurre en un script de Lua,
 * que Redis ejecuta de una vez: no hay un "entre" en el que otra petición o una caída se cuele.
 */
final class LimiteDeTasa {

    private static final int LIMITE = 5;
    private static final long VENTANA_MS = 1_000;

    private static final String SCRIPT = """
            local t = redis.call('TIME')
            local ahora = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local ventana = tonumber(ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ahora - ventana)
            if redis.call('ZCARD', KEYS[1]) < tonumber(ARGV[2]) then
              redis.call('ZADD', KEYS[1], ahora, ARGV[3])
              redis.call('PEXPIRE', KEYS[1], ventana)
              return 1
            end
            return 0
            """;

    private final RedisCommands<String, String> redis;

    LimiteDeTasa(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    boolean permitir(String cliente) {
        Long aceptada = redis.eval(SCRIPT, ScriptOutputType.INTEGER, new String[] {"tasa:" + cliente},
                String.valueOf(VENTANA_MS), String.valueOf(LIMITE), UUID.randomUUID().toString());
        return aceptada == 1;
    }
}
```

<!-- file: ejemplo/despues/src/Cerrojo.java -->
```java
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.Optional;
import java.util.UUID;

/**
 * Un cerrojo entre instancias, con sus dos correcciones:
 *   cada dueño tiene una ficha propia, y solo quien la tiene puede soltarlo (comparar y borrar, en un script);
 *   cada vez que alguien lo toma recibe un turno mayor que el anterior, para que el recurso rechace a un dueño caducado.
 * Lo que NO resuelve: si el dueño se congela más de lo que dura el cerrojo, deja de ser el único. Por eso el turno.
 */
final class Cerrojo {

    /** Lo que recibe quien consigue el cerrojo. */
    record Permiso(String recurso, String ficha, long turno) {
    }

    private static final String TOMAR = """
            if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then
              return redis.call('INCR', KEYS[2])
            end
            return 0
            """;

    private static final String SOLTAR = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """;

    private final RedisCommands<String, String> redis;

    Cerrojo(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    Optional<Permiso> tomar(String recurso, long vidaMs) {
        String ficha = UUID.randomUUID().toString();
        Long turno = redis.eval(TOMAR, ScriptOutputType.INTEGER, new String[] {"cerrojo:" + recurso, "cerrojo:" + recurso + ":turno"},
                ficha, String.valueOf(vidaMs));
        return turno == 0 ? Optional.empty() : Optional.of(new Permiso(recurso, ficha, turno));
    }

    /** Devuelve falso si el cerrojo ya no era de quien intenta soltarlo. */
    boolean soltar(Permiso permiso) {
        Long borradas = redis.eval(SOLTAR, ScriptOutputType.INTEGER, new String[] {"cerrojo:" + permiso.recurso()}, permiso.ficha());
        return borradas == 1;
    }
}
```

<!-- file: ejemplo/despues/src/LibroMayor.java -->
```java
import java.util.ArrayList;
import java.util.List;

/**
 * Lo que protege el cerrojo. Ahora recuerda el mayor turno que ha visto y rechaza a quien llega con uno menor:
 * eso es una ficha de cercado (fencing token). En un sistema real el libro es una base de datos y la comprobación
 * va en la escritura, como el filtro condicional de la sesión 03: updateOne({_id, turno: {$lt: t}}, ...).
 */
final class LibroMayor {

    private final List<String> asientos = new ArrayList<>();
    private long mayorTurno = 0;

    synchronized boolean escribir(long turno, String quien, String asiento) {
        if (turno < mayorTurno) {
            return false;
        }
        mayorTurno = turno;
        asientos.add(quien + " (turno " + turno + "): " + asiento);
        return true;
    }

    synchronized List<String> asientos() {
        return List.copyOf(asientos);
    }
}
```

<!-- file: ejemplo/despues/src/Main.java -->
```java
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Las mismas tres situaciones, con cada operación hecha atómica en Redis y con el límite del cerrojo cubierto. */
public class Main {

    public static void main(String[] args) {
        RedisClient cliente = RedisClient.create("redis://localhost:6379");
        try (StatefulRedisConnection<String, String> conexion = cliente.connect()) {
            RedisCommands<String, String> redis = conexion.sync();
            redis.flushdb();                                     // solo porque es el Redis del taller
            idempotencia(redis);
            limiteDeTasa(redis);
            cerrojo(redis);
        } finally {
            cliente.shutdown();
        }
    }

    private static void idempotencia(RedisCommands<String, String> redis) {
        System.out.println("1. Idempotencia: el móvil no recibió respuesta y reenvió el mismo pago 5 veces");
        Pasarela pasarela = new Pasarela();
        Idempotencia pagos = new Idempotencia(redis, pasarela);
        List<String> respuestas = aLaVez(5, () -> pagos.pagar("pago-77", "cuenta-1", 8_000_000));
        System.out.println("   respuestas: " + respuestas);
        System.out.println("   cobros en la pasarela: " + pasarela.cobros());
        System.out.println("   un reintento al rato: " + pagos.pagar("pago-77", "cuenta-1", 8_000_000));
        System.out.println("   la misma clave con otro valor: " + pagos.pagar("pago-77", "cuenta-1", 9_000_000));
        System.out.println("   la clave caduca en: " + redis.ttl("pago:pago-77") + " s");
    }

    private static void limiteDeTasa(RedisCommands<String, String> redis) {
        System.out.println("2a. Límite de 5 por segundo: una ráfaga justo en el cambio de ventana");
        LimiteDeTasa limite = new LimiteDeTasa(redis);
        limite.permitir("cliente-1");                            // t = 0
        dormir(900);
        int aceptadas = contarAceptadas(limite, "cliente-1", 4); // t = 900 ms
        dormir(150);
        aceptadas += contarAceptadas(limite, "cliente-1", 5);    // t = 1.050 ms
        System.out.println("   entre t=900 ms y t=1.050 ms se aceptaron " + aceptadas + " de 9");

        System.out.println("2b. Contar y caducar son un solo paso");
        System.out.println("   6 peticiones: aceptadas " + contarAceptadas(limite, "cliente-2", 6)
                + "; la clave caduca en: " + redis.pttl("tasa:cliente-2") + " ms");
        dormir(1_200);
        System.out.println("   1,2 s después, otra: " + (limite.permitir("cliente-2") ? "aceptada" : "rechazada"));
    }

    private static void cerrojo(RedisCommands<String, String> redis) {
        System.out.println("3. Cerrojo para el cierre del día, con vida de 1 s, ficha propia y turno");
        Cerrojo cerrojo = new Cerrojo(redis);
        LibroMayor libro = new LibroMayor();

        Cerrojo.Permiso deA = cerrojo.tomar("cierre", 1_000).orElseThrow();
        System.out.println("   A toma el cerrojo: sí, turno " + deA.turno());
        dormir(1_500);                                           // A se congela: una pausa larga del recolector de basura
        Cerrojo.Permiso deB = cerrojo.tomar("cierre", 1_000).orElseThrow();
        System.out.println("   B toma el cerrojo: sí, turno " + deB.turno());
        libro.escribir(deB.turno(), "B", "cierre del 15 de noviembre");
        System.out.println("   A despierta y escribe con su turno " + deA.turno() + ": "
                + (libro.escribir(deA.turno(), "A", "cierre del 15 de noviembre") ? "aceptado" : "rechazado"));
        System.out.println("   A intenta soltar el cerrojo: " + (cerrojo.soltar(deA) ? "lo borró" : "no era suyo, no borra nada"));
        Optional<Cerrojo.Permiso> deC = cerrojo.tomar("cierre", 1_000);
        System.out.println("   C toma el cerrojo mientras B sigue trabajando: " + (deC.isPresent() ? "sí" : "no"));
        System.out.println("   B termina y lo suelta: " + (cerrojo.soltar(deB) ? "lo borró" : "no era suyo"));
        System.out.println("   el libro: " + libro.asientos());
    }

    private static int contarAceptadas(LimiteDeTasa limite, String cliente, int peticiones) {
        int aceptadas = 0;
        for (int i = 0; i < peticiones; i++) {
            if (limite.permitir(cliente)) {
                aceptadas++;
            }
        }
        return aceptadas;
    }

    private static List<String> aLaVez(int veces, Callable<String> peticion) {
        List<Future<String>> enviadas = new ArrayList<>();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(veces)) {
            for (int i = 0; i < veces; i++) {
                enviadas.add(ejecutor.submit(peticion));
            }
        }
        List<String> respuestas = new ArrayList<>();
        for (Future<String> enviada : enviadas) {
            try {
                respuestas.add(enviada.get());
            } catch (InterruptedException | ExecutionException fallo) {
                throw new IllegalStateException(fallo);
            }
        }
        Collections.sort(respuestas);
        return respuestas;
    }

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }
}
```

```text
1. Idempotencia: el móvil no recibió respuesta y reenvió el mismo pago 5 veces
   respuestas: [201 aprobado-1, 409 en curso, reintenta, 409 en curso, reintenta, 409 en curso, reintenta, 409 en curso, reintenta]
   cobros en la pasarela: 1
   un reintento al rato: 200 aprobado-1 (repetida)
   la misma clave con otro valor: 422 la clave ya se usó con otra petición
   la clave caduca en: 86400 s
2a. Límite de 5 por segundo: una ráfaga justo en el cambio de ventana
   entre t=900 ms y t=1.050 ms se aceptaron 5 de 9
2b. Contar y caducar son un solo paso
   6 peticiones: aceptadas 5; la clave caduca en: 994 ms
   1,2 s después, otra: aceptada
3. Cerrojo para el cierre del día, con vida de 1 s, ficha propia y turno
   A toma el cerrojo: sí, turno 1
   B toma el cerrojo: sí, turno 2
   A despierta y escribe con su turno 1: rechazado
   A intenta soltar el cerrojo: no era suyo, no borra nada
   C toma el cerrojo mientras B sigue trabajando: no
   B termina y lo suelta: lo borró
   el libro: [B (turno 2): cierre del 15 de noviembre]
```

| Situación | Por qué ahora va bien |
|---|---|
| 1. Idempotencia | Un cobro. La reserva con `NX` la consigue una sola petición; las otras cuatro reciben `409` y, si reintentan, recibirán el mismo `aprobado-1`. La misma clave con otro valor recibe `422`. El resultado vive 24 horas y después desaparece |
| 2a. Ventana deslizante | Cinco de nueve: en cualquier segundo, como mucho cinco |
| 2b. Un solo paso | Contar y caducar ocurren dentro del script: la clave siempre tiene caducidad, y 1,2 s después el cliente vuelve a pasar |
| 3. Cerrojo con turno | A **siguió creyendo** que tenía el cerrojo: eso no lo puede evitar Redis. Lo evitó el libro, que ya había visto el turno 2 y rechazó el 1. A no pudo soltar el cerrojo de B, y C no entró |

Lo que el "después" no resuelve, y conviene decir en voz alta:

- Si el proceso cae después de cobrar y antes de guardar el resultado, la reserva caduca a los 30 s y un reintento cobrará otra vez. Solo una pasarela idempotente lo cierra.
- Si la pasarela falla por un tiempo de espera, no se sabe si cobró. El ejemplo borra la reserva para permitir el reintento; con una pasarela no idempotente, lo correcto sería marcar el pago como "desconocido" y conciliar.
- Si Redis pierde la clave (reinicio sin persistencia, conmutación por error, desalojo), la idempotencia, el límite y el cerrojo se olvidan. Los dos primeros degradan a "un poco peor"; el tercero, sin turno en el recurso, a "dos escriben a la vez".

## Errores comunes

- `GET` y luego `SET` para la idempotencia, en lugar de `SET … NX`.
- Guardar la clave de idempotencia sin caducidad, o con una más corta que la ventana de reintento del cliente.
- Responder a una repetición con un resultado distinto del original.
- Confiar en la clave de Redis para la consistencia de la propia base, en lugar de una restricción o un registro en la transacción.
- `INCR` y `EXPIRE` como dos órdenes separadas.
- Un límite de tasa que, si Redis cae, tumba el servicio (o lo deja abierto) sin que nadie lo haya decidido.
- Un cerrojo sin ficha, que cualquiera puede soltar.
- Un cerrojo sin turno protegiendo una escritura que no puede ocurrir dos veces.
- Renovar la vida del cerrojo desde un hilo aparte y creer que eso elimina el problema de la pausa: lo reduce.
- Guardar estas claves en la misma instancia que una caché con desalojo de cualquier clave.

## Taller

**Objetivo:** proteger tu servicio de pedidos de las repeticiones, del abuso y de las tareas duplicadas, sabiendo en cada caso qué pasa si Redis olvida.

Sobre tu servicio de pedidos del [módulo hexagonal](../../hexagonal/), con Lettuce, el [entorno](../entorno/) y, para los pedidos, tus adaptadores de MongoDB del [taller 04](../04-mongodb-en-el-banco-hexagonal/).

1. **Idempotencia.** Añade `Idempotency-Key` a "crear pedido" en tu adaptador HTTP. Lanza diez peticiones idénticas a la vez: un pedido, nueve respuestas `409` o con el mismo resultado. La misma clave con otro cuerpo: `422`.
2. Tu "crear pedido" cobra en un servicio de pagos simulado. Mata el proceso entre el cobro y el guardado del resultado (una pausa y `kill`) y demuestra el doble cobro al reintentar. Haz el servicio de pagos idempotente con la misma clave y demuestra que desaparece.
3. **Límite de tasa.** Diez pedidos por minuto por cliente, con un script de Lua (ventana deslizante o cubo de fichas, justifica la elección). Responde `429` con `Retry-After`. Prueba el borde de la ventana.
4. Detén Redis. Decide si tu límite falla abierto (deja pasar) o cerrado (rechaza) y escribe por qué, para tu caso. Impleméntalo y pruébalo.
5. **Cerrojo.** Una tarea que cancela los pedidos `PENDIENTE` de más de 24 horas corre en tres instancias. Protégela con un cerrojo con ficha y turno, y haz que la escritura en MongoDB compruebe el turno. Simula una pausa más larga que la vida del cerrojo y demuestra que el turno rechaza al dueño caducado.
6. Ahora discute si hacía falta el cerrojo: si la cancelación se escribe con un filtro `estado: "PENDIENTE"`, ¿qué pasa si dos instancias la ejecutan a la vez? ¿Para qué sirve entonces el cerrojo?
7. Con `maxmemory` bajo y `allkeys-lru`, llena Redis con claves de caché y demuestra que se desalojan claves de idempotencia. Propón la configuración correcta.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Diez peticiones idénticas crean un pedido y cobran una vez | Paso 1, repetido diez veces |
| El hueco entre cobrar y apuntar está demostrado y cerrado | Paso 2 |
| El límite nunca deja pasar más de lo pactado, ni en el borde | Paso 3 |
| Qué pasa sin Redis está decidido, implementado y escrito | Paso 4 |
| Un dueño caducado no puede escribir | Paso 5 |
| `ANALISIS.md` responde, para cada uso, "¿qué pasa si la clave desaparece?" | Lectura |

**Entrega:** rama `taller/nosql-06`, con el código, las salidas y `ANALISIS.md` en `taller/solucion/`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Spring no trae idempotencia por clave: se escribe como un filtro o un interceptor en el borde HTTP, con lo de esta sesión. Los límites de tasa suelen vivir en la pasarela de API, delante de los servicios; la de Spring Cloud Gateway, por ejemplo, implementa un cubo de fichas sobre Redis con un script de Lua. Para tareas programadas en varias instancias, bibliotecas como ShedLock guardan el cerrojo en Redis, MongoDB o la base relacional; y Redisson ofrece cerrojos que renuevan su vida mientras el dueño sigue vivo. Ninguna de las dos elimina el problema de la pausa: si la escritura no puede ocurrir dos veces, el turno o la condición tienen que estar en el recurso.

Cada vez que alguien proponga "lo guardamos en Redis", la pregunta de revisión es la de esta sesión: ¿qué pasa si mañana esa clave no está?

## Preguntas senior

1. ¿Por qué `SET … NX` y no `GET` y luego `SET`? ¿Qué tienen en común con el retiro de la sesión 03?
2. ¿Qué protege una clave de idempotencia en Redis que no protege el registro de órdenes en la transacción, y al revés?
3. ¿Qué hueco queda entre hacer el trabajo y guardar el resultado, y qué lo cierra de verdad?
4. ¿Por qué contar y caducar tienen que ser un solo paso, y cómo lo consigues en Redis?
5. Un cerrojo con caducidad, ficha y renovación: ¿puede haber dos dueños? ¿Qué lo impide en el recurso?
6. ¿Cuándo un cerrojo en Redis es suficiente y cuándo no?
7. Para cada uso de esta sesión: ¿qué pasa si Redis reinicia sin persistencia?
