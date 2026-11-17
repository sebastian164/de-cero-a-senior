# 01 — Qué es un pool de conexiones

## Idea

Una fila de taxis en el aeropuerto. Nadie fabrica un taxi cuando llega un pasajero ni lo desguaza cuando se baja: hay una fila de carros esperando, el pasajero toma el primero, hace su viaje y el taxi vuelve a la fila. Si hay más pasajeros que taxis, esperan su turno. Y si alguien se queda con el taxi y no lo devuelve, hay uno menos para todos.

## Lo esencial

### Por qué abrir una conexión es caro

Abrir una conexión a una base de datos no es crear un objeto. Es:

1. Establecer una conexión de red.
2. Negociar el cifrado.
3. Autenticarse.
4. Que la base cree un proceso o un hilo y reserve memoria para esa sesión.

Son decenas de milisegundos, muchas veces más que la consulta que se va a ejecutar. Y del lado de la base, cada conexión abierta cuesta memoria, se use o no.

### Qué hace un pool

Mantiene un conjunto de conexiones **ya abiertas** y las presta:

| Operación | Qué pasa |
|---|---|
| Pedir una conexión | Si hay una libre, se entrega al instante. Si no, se espera hasta un tiempo límite |
| Cerrarla | No se cierra: **vuelve al pool** |
| Mantenimiento | El pool retira las conexiones viejas o rotas y abre otras |

Lo que tu código llama `close()` significa "ya terminé, devuélvela". Por eso cerrar siempre, y pronto, es la regla más importante de todo este tema.

### HikariCP

Es el pool de conexiones que Spring Boot usa por defecto. Es pequeño, rápido y con pocos parámetros. Su documentación insiste en una idea que contradice la intuición: **un pool pequeño suele rendir más que uno grande**. La sesión 02 explica por qué.

### El pool es un límite, no solo una optimización

Además de ahorrar tiempo, el pool decide **cuántas operaciones hablan con la base a la vez**. Con un pool de diez, la petición número once espera. Eso protege a la base de una avalancha, y también significa que una conexión mal usada frena a todos los demás.

## Ejemplo

Un modelo en Java puro: abrir una conexión cuesta 20 ms y una consulta, 2 ms. No usa una base real ni HikariCP: aísla la idea. Ejecuta cada uno con `java Main.java`.

### Antes

<!-- file: ejemplo/antes/Main.java -->
```java
/** Una conexión nueva por cada petición. */
public class Main {

    static int conexionesAbiertas = 0;

    static final class Conexion {
        Conexion() {
            dormir(20);                      // red, cifrado, autenticación, sesión en la base
            conexionesAbiertas++;
        }

        String consultar(String sql) {
            dormir(2);
            return "resultado de: " + sql;
        }

        void cerrar() {
            // se destruye de verdad: la siguiente petición paga otra vez los 20 ms
        }
    }

    public static void main(String[] args) {
        long inicio = System.nanoTime();
        for (int peticion = 1; peticion <= 50; peticion++) {
            Conexion conexion = new Conexion();
            conexion.consultar("select * from pedidos where id = " + peticion);
            conexion.cerrar();
        }
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000;

        System.out.println("Peticiones atendidas: 50");
        System.out.println("Conexiones abiertas:  " + conexionesAbiertas);
        System.out.println("Tiempo total:         unos " + milisegundos + " ms");
    }

    static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### Análisis

| Problema | Consecuencia |
|---|---|
| Cincuenta conexiones para cincuenta peticiones | Más del 90 % del tiempo se va en abrir conexiones, no en consultar |
| Nada limita cuántas se abren a la vez | Con mil usuarios simultáneos se intentan mil conexiones. La base rechaza las que pasan de su límite, y las que acepta la dejan sin memoria |
| Cada apertura es una oportunidad de fallo de red | Más aperturas, más errores intermitentes |

### Después

<!-- file: ejemplo/despues/Main.java -->
```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/** Un pool mínimo: cinco conexiones que se abren una vez y se prestan. */
public class Main {

    static int conexionesAbiertas = 0;

    static final class Conexion {
        Conexion() {
            dormir(20);
            conexionesAbiertas++;
        }

        String consultar(String sql) {
            dormir(2);
            return "resultado de: " + sql;
        }
    }

    static final class Pool {
        private final BlockingQueue<Conexion> libres;

        Pool(int tamano) {
            libres = new ArrayBlockingQueue<>(tamano);
            for (int i = 0; i < tamano; i++) {
                libres.add(new Conexion());              // se pagan los 20 ms una sola vez por conexión
            }
        }

        Conexion tomar(long esperaMaximaMs) {
            try {
                Conexion conexion = libres.poll(esperaMaximaMs, TimeUnit.MILLISECONDS);
                if (conexion == null) {
                    throw new IllegalStateException("no hubo conexión disponible en " + esperaMaximaMs + " ms");
                }
                return conexion;
            } catch (InterruptedException interrupcion) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrumpido esperando una conexión");
            }
        }

        void devolver(Conexion conexion) {
            libres.add(conexion);                        // no se cierra: vuelve a la fila
        }
    }

    public static void main(String[] args) {
        long inicio = System.nanoTime();
        Pool pool = new Pool(5);
        for (int peticion = 1; peticion <= 50; peticion++) {
            Conexion conexion = pool.tomar(1000);
            try {
                conexion.consultar("select * from pedidos where id = " + peticion);
            } finally {
                pool.devolver(conexion);                 // siempre, pase lo que pase
            }
        }
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000;

        System.out.println("Peticiones atendidas: 50");
        System.out.println("Conexiones abiertas:  " + conexionesAbiertas);
        System.out.println("Tiempo total:         unos " + milisegundos + " ms");
    }

    static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }
}
```

- Cinco conexiones atienden las cincuenta peticiones, y el tiempo total baja a una fracción.
- Pedir una conexión tiene un tiempo límite: si no hay, se falla rápido en lugar de esperar para siempre.
- La devolución va en un `finally`. Con JDBC real, eso es un `try-with-resources`.

Con HikariCP no escribes nada de esto. El equivalente real es:

```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:postgresql://localhost:5432/tienda");
config.setUsername("postgres");
config.setPassword(clave);

try (HikariDataSource pool = new HikariDataSource(config)) {         // uno para toda la aplicación
    try (Connection conexion = pool.getConnection();                 // se toma del pool
         PreparedStatement consulta = conexion.prepareStatement("select nombre from productos where id = ?")) {
        consulta.setLong(1, 42);
        try (ResultSet filas = consulta.executeQuery()) {
            // usar las filas
        }
    }                                                                // close(): vuelve al pool
}
```

Este fragmento se compiló y se ejecutó tal cual contra PostgreSQL 17 con HikariCP 7.1.0, el controlador 42.7.13 y Java 21.

## Errores comunes

- Abrir una conexión por petición "porque es más simple".
- Crear un pool nuevo por cada petición o por cada clase. Es peor que no tener pool.
- Creer que `close()` cierra la conexión y por eso evitarlo.
- No cerrar: la conexión nunca vuelve.
- Pensar que un pool más grande siempre es mejor.
- Olvidar que cada instancia de la aplicación tiene **su propio** pool.

## Taller

**Objetivo:** medir con una base real cuánto cuesta abrir conexiones, y usar HikariCP sin Spring.

**Sobre tu servicio hexagonal:** si hiciste el [módulo de arquitectura hexagonal](../../hexagonal/), el pool se crea en la raíz de composición y se entrega al adaptador de tu puerto de transacciones, que es el único que pide conexiones. Mide el coste de abrir conexiones comparando ese adaptador con pool y sin él.

Necesitas PostgreSQL en un contenedor (el [entorno](../entorno/) lo trae) y un proyecto Java con el controlador de PostgreSQL y HikariCP.

1. Escribe un programa que ejecute `select 1` mil veces abriendo y cerrando una conexión cada vez con `DriverManager`. Mide el tiempo total y el promedio por operación.
2. Repite reutilizando una sola conexión. Compara.
3. Repite con HikariCP. Compara con los dos anteriores.
4. Mientras corre el paso 1, consulta en PostgreSQL cuántas conexiones hay (`select count(*) from pg_stat_activity`). Repite con el paso 3. ¿Qué ves?
5. Lanza el paso 1 desde cincuenta hilos a la vez contra una base con `max_connections=30`. Copia el error exacto. Repite con un pool de diez: ¿qué pasa con los hilos que no tienen conexión?
6. Con el pool, toma una conexión y **no la cierres**, once veces seguidas, con un pool de diez. ¿Qué ocurre en la undécima y cuánto tarda en ocurrir?
7. Activa el registro de HikariCP en nivel de depuración y describe lo que hace al arrancar y cada treinta segundos.
8. Crea dos `HikariDataSource` hacia la misma base por error, como haría un código que crea el pool en cada clase. ¿Cuántas conexiones ve PostgreSQL?
9. Cierra el pool al terminar el programa. ¿Qué pasa en la base si no lo cierras?

**Criterios de aceptación**

- Una tabla con los tiempos de los pasos 1 a 3 y su explicación.
- El error del paso 5 y el del paso 6, copiados tal cual.
- `ANALISIS.md` explica con tus palabras por qué `close()` no cierra.

**Entrega:** rama `taller/hikari-01`, con el código en `taller/solucion/` y `ANALISIS.md`.

## En el trabajo real

Con Spring Boot nunca creas el pool a mano: aparece solo en cuanto hay un controlador de base de datos y una URL configurada. Precisamente por eso mucha gente lleva años usándolo sin saber que existe, hasta el día en que aparece en un registro la frase `Connection is not available`.

## Preguntas senior

1. ¿Por qué es costoso abrir una conexión a una base de datos?
2. ¿Qué hace `close()` sobre una conexión que viene de un pool?
3. ¿En qué sentido un pool es un límite de concurrencia y no solo una optimización?
4. ¿Qué pasa cuando llegan más peticiones que conexiones hay?
5. Tienes diez instancias de un servicio con un pool de veinte. ¿Cuántas conexiones puede llegar a ver la base?
