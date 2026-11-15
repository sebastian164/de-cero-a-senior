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
