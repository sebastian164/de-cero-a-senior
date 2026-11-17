# 06 — El pool en un servicio hexagonal

> Requiere el módulo de [arquitectura hexagonal](../../hexagonal/), sobre todo la sesión de [transacciones](../../hexagonal/04-transacciones/). El ejemplo se compila **junto con el núcleo del banco**, sin modificarlo.

## Idea

Una biblioteca con cuatro mesas de consulta. Quien necesita trabajar con los libros ocupa una mesa y, al terminar, la deja. Si alguien ocupa una mesa y se va media hora a hacer una llamada, la mesa no sirve a nadie aunque nadie esté leyendo. Con cuatro personas así, la biblioteca está "llena" y vacía a la vez.

Las mesas son las conexiones del pool. La llamada de media hora es un servicio externo invocado con la transacción abierta.

## Lo esencial

### Dónde está el pool en la arquitectura

```
caso de uso ──► UnidadDeTrabajo (puerto) ──► adaptador ──► pool ──► base de datos
                                                 │
                              toma una conexión al empezar la transacción
                              y la devuelve al confirmar o revertir
```

| Pieza | Qué sabe del pool |
|---|---|
| Dominio | Nada |
| Aplicación | Nada. Solo dice "esto va junto" |
| Adaptador de la unidad de trabajo | Pide la conexión y la devuelve |
| Adaptadores de repositorio | Usan la conexión de la transacción en curso |
| Raíz de composición | Crea el pool y lo configura |

De ahí sale la relación que explica casi todos los incidentes:

**Una unidad de trabajo abierta es una conexión ocupada.** El tiempo que una conexión pasa fuera del pool es el tiempo que dura el caso de uso dentro de su unidad de trabajo, haga lo que haga ahí dentro.

### Lo que decide el caso de uso sin saberlo

El caso de uso no conoce el pool, pero su forma decide cuánto pool hace falta.

| Qué hace el caso de uso dentro de la unidad de trabajo | Efecto en el pool |
|---|---|
| Solo lecturas y escrituras en la base | La conexión se ocupa milisegundos |
| Llama a otro servicio | La conexión se ocupa lo que tarde el otro servicio |
| Publica en un broker y espera confirmación | Igual |
| Abre otra unidad de trabajo que pide **otra** conexión | Cada petición ocupa dos; con el pool lleno, se bloquea a sí misma |

Con la cuenta de la [sesión 02](../02-configuracion-que-importa/): las conexiones necesarias son las peticiones por segundo multiplicadas por los segundos que cada una retiene la conexión. A 50 peticiones por segundo, pasar de 10 ms a 200 ms de retención es pasar de necesitar 1 conexión a necesitar 10.

### Dos reglas de diseño

1. **Nada lento ni externo dentro de la unidad de trabajo.** Los eventos van a la bandeja de salida; las llamadas a otros servicios, antes o después.
2. **Una unidad de trabajo anidada se une a la que ya está abierta.** No pide otra conexión.

Las dos están en el módulo hexagonal por razones de corrección. Aquí se ve que también son razones de capacidad.

## Ejemplo

Java puro. `FondoDeConexiones` hace el papel de HikariCP: un número fijo de conexiones y un tiempo máximo de espera. El banco es el real del módulo hexagonal; solo cambia el adaptador de `UnidadDeTrabajo` que se le conecta.

```
ejemplo/src/
  fondo/FondoDeConexiones.java                       el doble de HikariCP
  fondo/UnidadDeTrabajoConFondo.java                 unidad de trabajo que ocupa una conexión
  banco/adaptadores/salida/jdbc/UnidadDeTrabajoJdbc.java   el adaptador real, con JDBC
  banco/adaptadores/salida/jdbc/CuentasJdbc.java           un repositorio real, con JDBC
  ConexionesDePrueba.java                            conexiones falsas que anotan lo que se les pide
  Main.java
```

### El doble del pool

<!-- file: ejemplo/src/fondo/FondoDeConexiones.java -->
```java
package fondo;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Lo esencial de un pool: un número fijo de conexiones y un tiempo máximo de espera para conseguir una. */
public final class FondoDeConexiones {

    public static final class SinConexion extends RuntimeException {

        private static final long serialVersionUID = 1L;

        SinConexion(long esperaMs) {
            super("no hubo conexión disponible tras " + esperaMs + " ms");
        }
    }

    private final Semaphore libres;
    private final long esperaMaximaMs;
    private final AtomicInteger enUso = new AtomicInteger();
    private final AtomicInteger maximoEnUso = new AtomicInteger();
    private final AtomicInteger agotamientos = new AtomicInteger();

    public FondoDeConexiones(int tamano, long esperaMaximaMs) {
        this.libres = new Semaphore(tamano, true);
        this.esperaMaximaMs = esperaMaximaMs;
    }

    public void tomar() {
        try {
            if (!libres.tryAcquire(esperaMaximaMs, TimeUnit.MILLISECONDS)) {
                agotamientos.incrementAndGet();
                throw new SinConexion(esperaMaximaMs);
            }
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupcion);
        }
        maximoEnUso.accumulateAndGet(enUso.incrementAndGet(), Math::max);
    }

    public void devolver() {
        enUso.decrementAndGet();
        libres.release();
    }

    public int maximoEnUso() {
        return maximoEnUso.get();
    }

    public int agotamientos() {
        return agotamientos.get();
    }
}
```

<!-- file: ejemplo/src/fondo/UnidadDeTrabajoConFondo.java -->
```java
package fondo;

import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.util.function.Supplier;

/** Envuelve una unidad de trabajo real y, como haría JDBC, ocupa una conexión mientras dura. */
public final class UnidadDeTrabajoConFondo implements UnidadDeTrabajo {

    private final FondoDeConexiones fondo;
    private final UnidadDeTrabajo real;
    private final boolean seUneALaAbierta;
    private final ThreadLocal<Boolean> tieneConexion = ThreadLocal.withInitial(() -> false);

    public UnidadDeTrabajoConFondo(FondoDeConexiones fondo, UnidadDeTrabajo real, boolean seUneALaAbierta) {
        this.fondo = fondo;
        this.real = real;
        this.seUneALaAbierta = seUneALaAbierta;
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        if (seUneALaAbierta && tieneConexion.get()) {
            return real.ejecutar(trabajo);                       // ya hay una transacción: usa su conexión
        }
        fondo.tomar();                                           // aquí se espera si el pool está lleno
        boolean antes = tieneConexion.get();
        tieneConexion.set(true);
        try {
            return real.ejecutar(trabajo);
        } finally {
            tieneConexion.set(antes);
            fondo.devolver();                                    // pase lo que pase
        }
    }
}
```

### El adaptador real, con JDBC

Esto es lo que va en producción en lugar del doble. Usa solo `java.sql` y `javax.sql`, que vienen con el JDK; el `DataSource` que recibe es, en un servicio real, un `HikariDataSource`.

<!-- file: ejemplo/src/banco/adaptadores/salida/jdbc/UnidadDeTrabajoJdbc.java -->
```java
package banco.adaptadores.salida.jdbc;

import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;
import javax.sql.DataSource;

/** El puerto de transacciones sobre JDBC. Es quien toma la conexión del pool y quien la devuelve. */
public final class UnidadDeTrabajoJdbc implements UnidadDeTrabajo {

    /** Una operación que necesita una conexión. */
    public interface Operacion<T> {

        T con(Connection conexion) throws SQLException;
    }

    private final DataSource pool;
    private final ThreadLocal<Connection> enCurso = new ThreadLocal<>();

    public UnidadDeTrabajoJdbc(DataSource pool) {
        this.pool = pool;
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        if (enCurso.get() != null) {
            return trabajo.get();                                // se une a la transacción abierta: no pide otra conexión
        }
        try (Connection conexion = pool.getConnection()) {       // sale del pool; espera, como mucho, connectionTimeout
            conexion.setAutoCommit(false);
            enCurso.set(conexion);
            try {
                T resultado = trabajo.get();
                conexion.commit();
                return resultado;
            } catch (RuntimeException | SQLException error) {
                conexion.rollback();
                throw error;
            } finally {
                enCurso.remove();
            }
        } catch (SQLException error) {
            throw new IllegalStateException("fallo de base de datos", error);
        }                                                        // al cerrarse, la conexión vuelve al pool
    }

    /** Para los repositorios: usa la conexión de la transacción en curso o, si no hay, una solo para esta operación. */
    public <T> T conConexion(Operacion<T> operacion) {
        try {
            Connection deLaTransaccion = enCurso.get();
            if (deLaTransaccion != null) {
                return operacion.con(deLaTransaccion);
            }
            try (Connection suelta = pool.getConnection()) {
                return operacion.con(suelta);
            }
        } catch (SQLException error) {
            throw new IllegalStateException("fallo de base de datos", error);
        }
    }
}
```

<!-- file: ejemplo/src/banco/adaptadores/salida/jdbc/CuentasJdbc.java -->
```java
package banco.adaptadores.salida.jdbc;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** El repositorio de cuentas con SQL a mano. No abre ni cierra conexiones: usa la de la unidad de trabajo. */
public final class CuentasJdbc implements RepositorioDeCuentas {

    private static final String INSERTAR = "insert into cuenta (titular, saldo_centavos, enviado_hoy_centavos, dia_del_tope, id, version) "
            + "values (?, ?, ?, ?, ?, 1)";
    private static final String ACTUALIZAR = "update cuenta set titular = ?, saldo_centavos = ?, enviado_hoy_centavos = ?, dia_del_tope = ?, "
            + "version = version + 1 where id = ? and version = ?";
    private static final String BUSCAR = "select titular, saldo_centavos, enviado_hoy_centavos, dia_del_tope, version from cuenta where id = ?";

    private final UnidadDeTrabajoJdbc transacciones;

    public CuentasJdbc(UnidadDeTrabajoJdbc transacciones) {
        this.transacciones = transacciones;
    }

    @Override
    public String siguienteId() {
        return "cuenta-" + UUID.randomUUID();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        boolean esNueva = cuenta.version() == 0;
        int filas = transacciones.conConexion(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement(esNueva ? INSERTAR : ACTUALIZAR)) {
                sentencia.setString(1, cuenta.titular());
                sentencia.setLong(2, cuenta.saldo().centavos());
                sentencia.setLong(3, cuenta.enviadoHoy().centavos());
                sentencia.setObject(4, cuenta.diaDelTope());
                sentencia.setString(5, cuenta.id());
                if (!esNueva) {
                    sentencia.setLong(6, cuenta.version());      // el bloqueo optimista, escrito a mano
                }
                return sentencia.executeUpdate();
            }
        });
        if (filas == 0) {
            throw new ConflictoDeConcurrencia("la cuenta " + cuenta.id());
        }
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        return transacciones.conConexion(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement(BUSCAR)) {
                sentencia.setString(1, id);
                try (ResultSet fila = sentencia.executeQuery()) {
                    if (!fila.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(Cuenta.existente(id, fila.getString("titular"),
                            new Dinero(fila.getLong("saldo_centavos")), new Dinero(fila.getLong("enviado_hoy_centavos")),
                            fila.getObject("dia_del_tope", LocalDate.class), fila.getLong("version")));
                }
            }
        });
    }
}
```

La tabla que esperan esas consultas, en PostgreSQL. Es la misma que crea el [`schema.sql` del proyecto JPA](../../hexagonal/proyecto-jpa/src/main/resources/schema.sql):

```sql
create table if not exists cuenta (
    id                    varchar(64)  primary key,
    titular               varchar(120) not null,
    saldo_centavos        bigint       not null check (saldo_centavos >= 0),
    enviado_hoy_centavos  bigint       not null check (enviado_hoy_centavos >= 0),
    dia_del_tope          date         not null,
    version               bigint       not null
);
```

Compara `guardar` con el de `AlmacenJpa` de la [sesión 07 del módulo hexagonal](../../hexagonal/07-persistencia-con-orm/): aquí el `where id = ? and version = ?` está a la vista; allí lo escribía Hibernate. El puerto es el mismo y el caso de uso no nota la diferencia. Los demás puertos de salida del banco (movimientos, órdenes atendidas, bandeja de salida) siguen el mismo patrón.

La raíz de composición, con HikariCP (compilada y ejecutada con HikariCP 7.1.0 contra PostgreSQL 17; los adaptadores `CuentasJdbc` y `UnidadDeTrabajoJdbc` son los de [`ejemplo/`](ejemplo/)):

```java
HikariConfig configuracion = new HikariConfig();
configuracion.setJdbcUrl("jdbc:postgresql://localhost:5432/banco");
configuracion.setUsername("banco");
configuracion.setPassword("banco");
configuracion.setMaximumPoolSize(10);
configuracion.setConnectionTimeout(3_000);               // fallar pronto es mejor que encolar medio minuto
configuracion.setLeakDetectionThreshold(2_000);          // avisa si una conexión pasa más de 2 s fuera del pool

UnidadDeTrabajoJdbc transacciones = new UnidadDeTrabajoJdbc(new HikariDataSource(configuracion));
RepositorioDeCuentas cuentas = new CuentasJdbc(transacciones);
```

Con `leakDetectionThreshold`, el aviso de HikariCP señalará la línea de `UnidadDeTrabajoJdbc` que tomó la conexión. Como hay un solo sitio que las toma, lo que te dirá de verdad es **qué caso de uso** tardó.

### Conexiones falsas para probar el adaptador

Sin base de datos se puede comprobar lo que más importa del adaptador: que confirma o revierte, y que **siempre** devuelve la conexión.

<!-- file: ejemplo/src/ConexionesDePrueba.java -->
```java
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/** Un DataSource cuyas conexiones no hacen nada salvo anotar qué se les pidió. */
final class ConexionesDePrueba {

    private final List<String> anotado = new ArrayList<>();
    private int entregadas = 0;

    DataSource pool() {
        return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {DataSource.class},
                (proxy, metodo, argumentos) -> {
                    if (metodo.getName().equals("getConnection")) {
                        entregadas++;
                        anotado.add("tomar");
                        return conexion();
                    }
                    throw new UnsupportedOperationException(metodo.getName());
                });
    }

    private Connection conexion() {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, metodo, argumentos) -> {
                    switch (metodo.getName()) {
                        case "commit" -> anotado.add("confirmar");
                        case "rollback" -> anotado.add("revertir");
                        case "close" -> anotado.add("devolver");
                        default -> {
                            // setAutoCommit y lo demás: nada que anotar
                        }
                    }
                    return null;
                });
    }

    List<String> anotado() {
        return List.copyOf(anotado);
    }

    int entregadas() {
        return entregadas;
    }
}
```

### La demostración

<!-- file: ejemplo/src/Main.java -->
```java
import banco.adaptadores.salida.jdbc.UnidadDeTrabajoJdbc;
import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import banco.aplicacion.ServicioDeCuentas;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.puertos.salida.Reloj;
import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import fondo.FondoDeConexiones;
import fondo.FondoDeConexiones.SinConexion;
import fondo.UnidadDeTrabajoConFondo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** El mismo banco, con un pool de 4 conexiones debajo de su unidad de trabajo. */
public class Main {

    private static final Reloj RELOJ = () -> Instant.parse("2026-11-15T10:00:00Z");
    private static final int PETICIONES = 16;
    private static int fallos = 0;

    public static void main(String[] args) {
        System.out.println("1. El evento se entrega a un servicio lento DENTRO de la unidad de trabajo");
        FondoDeConexiones fondoLento = new FondoDeConexiones(4, 300);
        AlmacenEnMemoria almacenLento = new AlmacenEnMemoria();
        PublicadorDeEventos servicioLento = evento -> dormir(200);            // 200 ms con la conexión ocupada
        int[] lento = transferirALaVez(almacenLento, fondoLento, servicioLento);
        System.out.println("     " + lento[0] + " realizadas, " + lento[1] + " sin conexión, en " + lento[2] + " ms");
        comprobar("el pool de 4 se agota: hay peticiones que no consiguen conexión", lento[1] > 0);
        comprobar("y nunca hubo más de 4 en uso", fondoLento.maximoEnUso() == 4);

        System.out.println("2. El mismo caso de uso, con el evento en la bandeja de salida");
        FondoDeConexiones fondoRapido = new FondoDeConexiones(4, 300);
        AlmacenEnMemoria almacenRapido = new AlmacenEnMemoria();
        int[] rapido = transferirALaVez(almacenRapido, fondoRapido, almacenRapido);
        System.out.println("     " + rapido[0] + " realizadas, " + rapido[1] + " sin conexión, en " + rapido[2] + " ms");
        comprobar("las 16 terminan con el mismo pool de 4", rapido[0] == PETICIONES && fondoRapido.agotamientos() == 0);
        AtomicInteger entregados = new AtomicInteger();
        almacenRapido.entregarPendientes(evento -> {
            dormir(20);                                                       // el servicio sigue siendo lento…
            entregados.incrementAndGet();
        });
        comprobar("y los 16 eventos se entregan después, sin ocupar ninguna conexión", entregados.get() == PETICIONES);

        System.out.println("3. Una unidad de trabajo dentro de otra, con un pool de 1");
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        UnidadDeTrabajo pideOtra = new UnidadDeTrabajoConFondo(new FondoDeConexiones(1, 200), almacen, false);
        try {
            pideOtra.ejecutar(() -> pideOtra.ejecutar(() -> "dentro"));
            comprobar("debía quedarse sin conexión", false);
        } catch (SinConexion bloqueo) {
            comprobar("si la de dentro pide otra conexión, espera la que tiene ella misma: " + bloqueo.getMessage(), true);
        }
        UnidadDeTrabajo seUne = new UnidadDeTrabajoConFondo(new FondoDeConexiones(1, 200), almacen, true);
        comprobar("si se une a la abierta, funciona con una sola", seUne.ejecutar(() -> seUne.ejecutar(() -> "dentro")).equals("dentro"));

        System.out.println("4. El adaptador JDBC, con conexiones que anotan lo que se les pide");
        ConexionesDePrueba conexiones = new ConexionesDePrueba();
        UnidadDeTrabajoJdbc jdbc = new UnidadDeTrabajoJdbc(conexiones.pool());
        jdbc.ejecutar(() -> jdbc.ejecutar(() -> "anidada"));
        comprobar("si todo va bien: tomar, confirmar, devolver; y una sola conexión aunque haya anidamiento",
                conexiones.anotado().equals(List.of("tomar", "confirmar", "devolver")) && conexiones.entregadas() == 1);
        try {
            jdbc.ejecutar(() -> {
                throw new IllegalStateException("el caso de uso falló");
            });
        } catch (IllegalStateException esperado) {
            comprobar("si el caso de uso falla: tomar, revertir, devolver",
                    conexiones.anotado().equals(List.of("tomar", "confirmar", "devolver", "tomar", "revertir", "devolver")));
        }

        System.out.println(fallos == 0 ? "Todo correcto" : fallos + " comprobaciones fallaron");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    /** 16 transferencias a la vez entre cuentas distintas. Devuelve realizadas, sin conexión y milisegundos. */
    private static int[] transferirALaVez(AlmacenEnMemoria almacen, FondoDeConexiones fondo, PublicadorDeEventos eventos) {
        UnidadDeTrabajo conFondo = new UnidadDeTrabajoConFondo(fondo, almacen, true);
        ServicioDeCuentas banco = new ServicioDeCuentas(almacen, almacen, almacen, conFondo, RELOJ, eventos);
        List<String> origenes = new ArrayList<>();
        List<String> destinos = new ArrayList<>();
        for (int i = 0; i < PETICIONES; i++) {
            origenes.add(banco.abrir("Origen " + i, 10_000_000).id());
            destinos.add(banco.abrir("Destino " + i, 0).id());
        }
        AtomicInteger realizadas = new AtomicInteger();
        AtomicInteger sinConexion = new AtomicInteger();
        long inicio = System.nanoTime();
        try (ExecutorService peticiones = Executors.newFixedThreadPool(PETICIONES)) {
            for (int i = 0; i < PETICIONES; i++) {
                int numero = i;
                peticiones.submit(() -> {
                    try {
                        banco.transferir(new Orden("orden-" + numero, origenes.get(numero), destinos.get(numero), 1_000_000));
                        realizadas.incrementAndGet();
                    } catch (SinConexion agotado) {
                        sinConexion.incrementAndGet();
                    }
                });
            }
        }
        return new int[] {realizadas.get(), sinConexion.get(), (int) ((System.nanoTime() - inicio) / 1_000_000)};
    }

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }

    private static void comprobar(String que, boolean cumple) {
        System.out.println((cumple ? "   ok    " : "   FALLA ") + que);
        if (!cumple) {
            fallos++;
        }
    }
}
```

Desde `ejemplo/`:

```bash
javac -Xlint:all -Werror -d salida -sourcepath ../../../hexagonal/proyecto/src $(find src -name '*.java')
java -cp salida Main
```

### Análisis

| Paso | Qué demuestra |
|---|---|
| 1 | Un caso de uso correcto en lo funcional agota un pool de 4 con 16 peticiones, solo por lo que hace dentro de la transacción |
| 2 | El mismo pool atiende las mismas 16 sin un solo fallo cuando lo lento sale de la transacción |
| 3 | Pedir una segunda conexión con una ya en la mano es un bloqueo contra uno mismo. Con el pool de 1 se ve siempre; con uno grande, solo bajo carga |
| 4 | El adaptador devuelve la conexión en todos los caminos, y el anidamiento no pide otra |

En el paso 1, los conteos exactos dependen de la máquina: usa hilos y tiempos reales. Lo que no cambia es que hay peticiones sin conexión.

El arreglo del paso 2 **no tocó el tamaño del pool ni el tiempo de espera**. Tampoco tocó el caso de uso: cambió el adaptador conectado al puerto `PublicadorDeEventos`. La decisión que arregla el incidente es de arquitectura.

## Errores comunes

- Repositorios que piden su propia conexión al pool en lugar de usar la de la transacción.
- Una unidad de trabajo anidada que abre otra conexión.
- Llamadas HTTP o publicaciones en un broker dentro de la unidad de trabajo.
- Abrir la transacción en el controlador: la conexión queda ocupada mientras se serializa la respuesta.
- Olvidar devolver la conexión en el camino de error.
- Subir `maximumPoolSize` ante un agotamiento sin mirar qué hacen los casos de uso con la conexión.
- Diagnosticar el agotamiento mirando los repositorios, cuando la retención la decide el caso de uso.

## Taller

**Objetivo:** poner un pool real debajo de tu servicio de pedidos, agotarlo a propósito con una decisión de diseño y arreglarlo sin cambiar su tamaño.

Sobre tu servicio de pedidos del [módulo hexagonal](../../hexagonal/) y el [entorno](../entorno/) de esta ruta. Necesitas HikariCP, el controlador de PostgreSQL y una herramienta de carga.

1. Escribe el adaptador de tu puerto de transacciones con JDBC y HikariCP. Debe ser el único sitio que pide y devuelve conexiones.
2. Escribe con JDBC los repositorios que necesite "crear pedido". No deben pedir conexiones: usan la de la transacción en curso.
3. Sin base de datos, con conexiones falsas, prueba el adaptador: confirma si todo va bien, revierte si algo falla, devuelve la conexión siempre, y una unidad de trabajo anidada no pide otra.
4. Ejecuta el contrato de repositorio que escribiste en el módulo hexagonal contra los repositorios JDBC, con PostgreSQL real.
5. Mide cuánto tiempo retiene la conexión "crear pedido" y calcula, con la cuenta de la sesión 02, el tamaño de pool para la carga que elijas. Compruébalo con carga real.
6. Introduce el defecto: dentro de la unidad de trabajo, llama a un servicio que tarda 300 ms (un servidor de pega basta). Repite la carga y recoge la evidencia: errores, tiempos y las métricas del pool.
7. Activa `leakDetectionThreshold` y lee el aviso. ¿Qué línea señala y qué te dice eso sobre dónde buscar? (HikariCP desactiva, con un aviso en el registro, los umbrales menores de 2000 ms, como se comprobó con la 7.1.0: con el servicio de 300 ms no saltará nada. Para ver el aviso, sube la demora del servicio a 2,5 s y el umbral a 2000.)
8. Arréglalo sin tocar `maximumPoolSize` ni `connectionTimeout`. Repite la carga y compara.
9. Con el pool en 1, provoca el bloqueo de una unidad de trabajo anidada que pide otra conexión. Luego corrígelo.
10. Escribe en una línea, para un compañero, la regla que relaciona tus casos de uso con el tamaño del pool.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Solo el adaptador de transacciones toca el pool | Búsqueda de `getConnection` |
| El dominio y la aplicación no cambiaron | `git diff` de esos paquetes |
| El adaptador devuelve la conexión en todos los caminos | Las pruebas del paso 3 |
| El agotamiento se reproduce y se corrige sin cambiar el tamaño | Las mediciones de los pasos 6 y 8 |
| El tamaño del pool sale de una cuenta comprobada con carga | El paso 5 |

**Entrega:** rama `taller/hikari-06`, con el código, las mediciones y `ANALISIS.md` en `taller/solucion/`.

## En el trabajo real

En Spring no escribes este adaptador: `@Transactional` toma la conexión al entrar y la devuelve al salir, y los repositorios usan la de la transacción en curso. Lo que sí sigue siendo tuyo es todo lo que demuestra esta sesión: qué hace cada caso de uso mientras tiene la conexión. Dos detalles que conviene conocer: con `open-in-view` activado, la conexión puede quedar retenida hasta que se termina de escribir la respuesta, y una propagación `REQUIRES_NEW` pide una segunda conexión con la primera en la mano, que es exactamente el paso 3.

Cuando el pool se agota en producción, la pregunta que encuentra la causa no es "¿cuántas conexiones tenemos?" sino "¿qué caso de uso las retiene y qué hace mientras tanto?".

## Preguntas senior

1. ¿Qué pieza de una arquitectura hexagonal toma y devuelve las conexiones?
2. ¿Por qué la forma de un caso de uso determina el tamaño del pool?
3. ¿Qué ocurre si una transacción anidada pide otra conexión y el pool está lleno?
4. ¿Por qué la bandeja de salida es también una decisión de capacidad?
5. Ante un agotamiento, ¿qué miras antes de subir el tamaño del pool?
6. ¿Qué puedes probar del adaptador de transacciones sin una base de datos?
