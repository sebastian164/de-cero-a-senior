import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Prepared statements de servidor a través de PgBouncer en modo transacción. Uso: java -cp postgresql.jar Preparadas.java url usuario clave
 * Con prepareThreshold=1 el controlador prepara la sentencia en el servidor desde la primera ejecución y la reutiliza por nombre.
 * Ocho clientes concurrentes alternan entre dos sentencias en muchas transacciones: PgBouncer reparte las transacciones
 * entre las conexiones del servidor. Imprime "ok N" (transacciones bien) o "error: mensaje" (el primero).
 */
public class Preparadas {

    public static void main(String[] args) throws Exception {
        java.util.concurrent.atomic.AtomicInteger bien = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> primero = new java.util.concurrent.atomic.AtomicReference<>();
        Thread[] hilos = new Thread[8];
        for (int h = 0; h < hilos.length; h++) {
            hilos[h] = new Thread(() -> {
                try (Connection c = DriverManager.getConnection(args[0] + "?prepareThreshold=1", args[1], args[2])) {
                    c.setAutoCommit(false);
                    for (int i = 0; i < 300; i++) {
                        String sql = i % 2 == 0 ? "select ? + 1" : "select ? * 2";
                        try (PreparedStatement s = c.prepareStatement(sql)) {
                            s.setInt(1, i);
                            try (ResultSet r = s.executeQuery()) {
                                r.next();
                            }
                        }
                        c.commit();
                        bien.incrementAndGet();
                    }
                } catch (Exception e) {
                    primero.compareAndSet(null, e.getMessage().lines().findFirst().orElse(""));
                }
            });
            hilos[h].start();
        }
        for (Thread hilo : hilos) {
            hilo.join();
        }
        System.out.println(primero.get() == null ? "ok " + bien.get() : "error: " + primero.get() + " (bien: " + bien.get() + ")");
    }
}
