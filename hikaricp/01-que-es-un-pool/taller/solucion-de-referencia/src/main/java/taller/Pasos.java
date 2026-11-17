package taller;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Los pasos del taller 01. Cada método es un experimento; las pruebas los ejecutan y comprueban lo que muestran. */
public final class Pasos {

    public record Medicion(String nombre, int veces, long totalMs) {
        public double promedioMs() {
            return (double) totalMs / veces;
        }
    }

    /** Resultado de lanzar muchos hilos: cuántas operaciones salieron bien y qué errores aparecieron, con su número. */
    public record Concurrencia(int correctas, Map<String, Integer> errores, long totalMs) {
    }

    private final String url;
    private final String usuario;
    private final String clave;

    public Pasos(String url, String usuario, String clave) {
        this.url = url;
        this.usuario = usuario;
        this.clave = clave;
    }

    public HikariDataSource pool(int tamano) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(usuario);
        config.setPassword(clave);
        config.setMaximumPoolSize(tamano);
        return new HikariDataSource(config);
    }

    /** Paso 1: abrir y cerrar una conexión por cada select 1. */
    public Medicion abriendoCadaVez(int veces) throws SQLException {
        long inicio = System.nanoTime();
        for (int i = 0; i < veces; i++) {
            try (Connection conexion = DriverManager.getConnection(url, usuario, clave)) {
                seleccionarUno(conexion);
            }
        }
        return new Medicion("DriverManager, una conexión nueva por consulta", veces, ms(inicio));
    }

    /** Paso 2: una sola conexión para todas. */
    public Medicion unaSolaConexion(int veces) throws SQLException {
        long inicio = System.nanoTime();
        try (Connection conexion = DriverManager.getConnection(url, usuario, clave)) {
            for (int i = 0; i < veces; i++) {
                seleccionarUno(conexion);
            }
        }
        return new Medicion("DriverManager, una sola conexión reutilizada", veces, ms(inicio));
    }

    /** Paso 3: pedir y devolver al pool en cada consulta. No cuenta el arranque del pool. */
    public Medicion conHikari(HikariDataSource pool, int veces) throws SQLException {
        long inicio = System.nanoTime();
        for (int i = 0; i < veces; i++) {
            try (Connection conexion = pool.getConnection()) {
                seleccionarUno(conexion);
            }
        }
        return new Medicion("HikariCP, getConnection() y close() por consulta", veces, ms(inicio));
    }

    /** Pasos 5: la carga del paso 1 desde muchos hilos a la vez, sin pool. */
    public Concurrencia hilosSinPool(int hilos, int vecesPorHilo) throws InterruptedException {
        return enHilos(hilos, vecesPorHilo, () -> {
            try (Connection conexion = DriverManager.getConnection(url, usuario, clave)) {
                seleccionarUno(conexion);
            }
        });
    }

    /** Paso 5, segunda parte: la misma carga, con un pool. */
    public Concurrencia hilosConPool(HikariDataSource pool, int hilos, int vecesPorHilo) throws InterruptedException {
        return enHilos(hilos, vecesPorHilo, () -> {
            try (Connection conexion = pool.getConnection()) {
                seleccionarUno(conexion);
            }
        });
    }

    interface Trabajo {
        void hacer() throws SQLException;
    }

    private static Concurrencia enHilos(int hilos, int vecesPorHilo, Trabajo trabajo) throws InterruptedException {
        AtomicInteger correctas = new AtomicInteger();
        Map<String, Integer> errores = new ConcurrentHashMap<>();
        long inicio = System.nanoTime();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(hilos)) {
            for (int h = 0; h < hilos; h++) {
                ejecutor.submit(() -> {
                    for (int i = 0; i < vecesPorHilo; i++) {
                        try {
                            trabajo.hacer();
                            correctas.incrementAndGet();
                        } catch (SQLException error) {
                            errores.merge(error.getClass().getSimpleName() + ": " + error.getMessage(), 1, Integer::sum);
                        }
                    }
                });
            }
        }
        return new Concurrencia(correctas.get(), new TreeMap<>(errores), ms(inicio));
    }

    static void seleccionarUno(Connection conexion) throws SQLException {
        try (Statement sentencia = conexion.createStatement(); ResultSet fila = sentencia.executeQuery("select 1")) {
            fila.next();
        }
    }

    static long ms(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }
}
