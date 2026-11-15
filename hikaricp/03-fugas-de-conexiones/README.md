# 03 — Fugas de conexiones

## Idea

Los carritos del supermercado. Hay treinta. Si cada cliente lo deja en su sitio al salir, alcanzan para todo el día. Si uno de cada veinte se lo lleva hasta el carro y lo abandona en el parqueadero, a media tarde no queda ninguno en la entrada y la gente espera en la puerta, aunque la tienda esté medio vacía. Nadie hizo nada grave; simplemente no devolvió.

## Lo esencial

Una **fuga** ocurre cuando el código toma una conexión del pool y no la devuelve. El pool la sigue contando como en uso. Cuando todas se han fugado, nadie más puede trabajar.

### Por qué es tan difícil de ver

- No falla donde está el error. Falla **después**, en una petición cualquiera que tuvo la mala suerte de ser la siguiente.
- Depende del camino del código: normalmente la fuga está en una rama poco transitada, la de un error o un caso raro.
- Es lenta. Un pool de diez con una fuga por cada mil peticiones tarda horas en vaciarse. Suele aparecer de madrugada o tras un día de mucho tráfico, y "se arregla" reiniciando.

### Dónde nacen

| Lugar | Ejemplo |
|---|---|
| Una salida anticipada | Un `return` antes del `close()` |
| Una excepción | Se lanza entre tomar la conexión y devolverla, sin `finally` |
| Recursos anidados | Se cierra la conexión pero falla antes de cerrar la consulta o el resultado |
| Código que guarda la conexión | En un campo, en una caché, "para reutilizarla" |
| Una transacción que nunca termina | Se abre a mano y un camino no hace ni confirmación ni reversión |
| Hilos propios | Un trabajo en segundo plano que toma una conexión y se queda bloqueado |

### La defensa

**`try-with-resources`, siempre.** La conexión, la consulta y el resultado se declaran en el `try` y Java los cierra en todos los caminos, incluidos los de error. Con Spring, usar `JdbcTemplate`, repositorios o `@Transactional` hace lo mismo por ti; las fugas aparecen cuando alguien pide la conexión a mano.

### Cómo encontrarlas

| Herramienta | Qué te da |
|---|---|
| `leakDetectionThreshold` | Si una conexión lleva prestada más de ese tiempo, HikariCP registra un aviso con **la traza del código que la tomó**. Es la pista más directa que existe |
| Métrica de conexiones activas | Una fuga se ve como una línea que sube en escalera y nunca baja, aunque el tráfico baje |
| Sesiones de la base | En PostgreSQL, `pg_stat_activity` muestra conexiones en estado `idle in transaction` con horas de antigüedad |

Un aviso de fuga no siempre es una fuga: también salta con una operación legítimamente larga. La diferencia es si la conexión acaba volviendo.

## Ejemplo

Java puro, con un pool de tres conexiones. Ejecuta cada uno con `java Main.java`.

### Antes

<!-- file: ejemplo/antes/Main.java -->
```java
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Un método que devuelve la conexión solo cuando todo sale bien. */
public class Main {

    static final class Pool {
        private final int tamano;
        private final Semaphore libres;

        Pool(int tamano) {
            this.tamano = tamano;
            this.libres = new Semaphore(tamano);
        }

        Conexion tomar() {
            try {
                if (!libres.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Connection is not available, request timed out after 100ms");
                }
            } catch (InterruptedException interrupcion) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrumpido");
            }
            return new Conexion(this);
        }

        int enUso() {
            return tamano - libres.availablePermits();
        }
    }

    static final class Conexion {
        private final Pool pool;

        Conexion(Pool pool) {
            this.pool = pool;
        }

        String consultar(String id) {
            return "cliente " + id;
        }

        void cerrar() {
            pool.libres.release();
        }
    }

    static String buscarCliente(Pool pool, String id) {
        Conexion conexion = pool.tomar();
        if (id.isBlank()) {
            return "sin identificador";                              // sale sin devolver
        }
        if (id.equals("desconocido")) {
            throw new IllegalArgumentException("el cliente no existe");   // sale sin devolver
        }
        String cliente = conexion.consultar(id);
        conexion.cerrar();                                           // solo se llega aquí si todo va bien
        return cliente;
    }

    public static void main(String[] args) {
        Pool pool = new Pool(3);
        for (String id : new String[] {"7", "", "desconocido", "8", "", "9"}) {
            try {
                String resultado = buscarCliente(pool, id);
                System.out.println("buscar '" + id + "' -> " + resultado + "   (en uso: " + pool.enUso() + " de 3)");
            } catch (RuntimeException error) {
                System.out.println("buscar '" + id + "' -> ERROR: " + error.getMessage() + "   (en uso: " + pool.enUso() + " de 3)");
            }
        }
    }
}
```

### Análisis

| Lo que se ve | Lo que pasó |
|---|---|
| Las búsquedas con datos normales funcionan | El camino feliz sí devuelve |
| Tras cada búsqueda rara, "en uso" sube y no vuelve a bajar | Dos caminos salen sin devolver la conexión |
| La búsqueda de `9`, que es correcta, falla | El error aparece en una petición inocente. Quien mire ese error buscará el problema en el lugar equivocado |
| El mensaje habla de un tiempo de espera | Invita a "subir el tiempo de espera" o "subir el pool", que solo retrasan el fallo |

### Después

<!-- file: ejemplo/despues/Main.java -->
```java
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** El mismo método con try-with-resources: la conexión vuelve por todos los caminos. */
public class Main {

    static final class Pool {
        private final int tamano;
        private final Semaphore libres;

        Pool(int tamano) {
            this.tamano = tamano;
            this.libres = new Semaphore(tamano);
        }

        Conexion tomar() {
            try {
                if (!libres.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Connection is not available, request timed out after 100ms");
                }
            } catch (InterruptedException interrupcion) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrumpido");
            }
            return new Conexion(this);
        }

        int enUso() {
            return tamano - libres.availablePermits();
        }
    }

    static final class Conexion implements AutoCloseable {
        private final Pool pool;

        Conexion(Pool pool) {
            this.pool = pool;
        }

        String consultar(String id) {
            return "cliente " + id;
        }

        @Override
        public void close() {
            pool.libres.release();
        }
    }

    static String buscarCliente(Pool pool, String id) {
        if (id.isBlank()) {
            return "sin identificador";                  // ni siquiera hace falta una conexión para decidir esto
        }
        try (Conexion conexion = pool.tomar()) {
            if (id.equals("desconocido")) {
                throw new IllegalArgumentException("el cliente no existe");
            }
            return conexion.consultar(id);
        }                                                // close() se ejecuta aquí, también si hubo excepción
    }

    public static void main(String[] args) {
        Pool pool = new Pool(3);
        for (String id : new String[] {"7", "", "desconocido", "8", "", "9"}) {
            try {
                String resultado = buscarCliente(pool, id);
                System.out.println("buscar '" + id + "' -> " + resultado + "   (en uso: " + pool.enUso() + " de 3)");
            } catch (RuntimeException error) {
                System.out.println("buscar '" + id + "' -> ERROR: " + error.getMessage() + "   (en uso: " + pool.enUso() + " de 3)");
            }
        }
    }
}
```

- "En uso" vuelve a cero después de cada búsqueda, haya ido bien o mal.
- La búsqueda de `9` funciona.
- De paso, la validación que no necesita la base se hace **antes** de pedir la conexión: cuanto menos tiempo se retiene, mejor.

## Errores comunes

- `close()` al final del método, fuera de un `finally`.
- Cerrar la conexión y olvidar la consulta y el resultado.
- Guardar una conexión en un campo.
- Subir el tamaño del pool cuando hay una fuga: el fallo llega igual, más tarde.
- Reiniciar el servicio cada noche "porque se pone lento".
- Detección de fugas desactivada en todos los ambientes, o con un umbral tan bajo que avisa de todo.
- Tratar un aviso de fuga como ruido sin mirar la traza.

## Taller

**Objetivo:** provocar una fuga con HikariCP real, encontrarla con las herramientas y no con la vista, y eliminarla.

Con el [entorno](../entorno/) y una aplicación Spring Boot con un pool de cinco.

1. Escribe un extremo que tome la conexión a mano desde el `DataSource` y que tenga una fuga en un camino poco frecuente (por ejemplo, cuando el parámetro es negativo). No uses `try-with-resources` todavía.
2. Lanza tráfico en el que una de cada cincuenta peticiones pase por ese camino. Registra la métrica de conexiones activas cada pocos segundos. Dibuja la gráfica.
3. ¿En qué petición aparece el error y qué relación tiene con el código defectuoso? Copia el mensaje completo.
4. Sube el pool a 20 y repite. ¿Qué cambió? ¿Qué te dice eso de "subir el pool" como solución?
5. Activa `leak-detection-threshold` con un valor razonable. Repite. Lee el aviso: ¿a qué línea de tu código apunta la traza?
6. Mira `pg_stat_activity` durante la fuga. ¿En qué estado están las conexiones fugadas?
7. Corrige con `try-with-resources`. Repite la carga: la gráfica de conexiones activas debe volver a cero cuando para el tráfico.
8. Otra fuga, más sutil: abre una transacción a mano (`setAutoCommit(false)`) y deja un camino sin confirmar ni revertir, pero cerrando la conexión. ¿Se fuga la conexión? ¿Qué pasa con los bloqueos que tomó? Averigua qué hace HikariCP con una conexión que se devuelve con una transacción abierta.
9. Una falsa alarma: haz una operación legítima que tarde más que tu umbral de detección. ¿Cómo distingues ese aviso de una fuga de verdad?
10. Reescribe el extremo con `JdbcTemplate`. ¿Dónde quedó la gestión de la conexión?
11. Escribe una prueba que detecte una fuga: ejecuta el extremo por todos sus caminos y comprueba que las conexiones activas terminan en cero.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| La fuga se localizó con la traza de detección, no leyendo el código | Paso 5 |
| Tras la corrección, las conexiones activas vuelven a cero | Paso 7, con la gráfica |
| Hay una prueba automática que fallaría si la fuga volviera | Paso 11 |
| Se entiende por qué subir el pool no arregla nada | Paso 4 |

**Entrega:** rama `taller/hikari-03`, con el código en `taller/solucion/` y `ANALISIS.md`.

## En el trabajo real

Cuando un servicio "se cuelga cada cierto tiempo y se arregla reiniciando", una fuga de conexiones es de las primeras sospechas. Dejar la detección de fugas activada, con un umbral holgado, cuesta casi nada y convierte una investigación de días en la lectura de una traza.

## Preguntas senior

1. ¿Qué es una fuga de conexiones y por qué el error aparece lejos de su causa?
2. ¿Cómo la detectas en producción?
3. ¿Por qué `try-with-resources` y no un `close()` al final?
4. ¿Qué diferencia hay entre una fuga y una operación lenta, vistas desde el pool?
5. ¿Por qué aumentar el tamaño del pool no soluciona una fuga?
