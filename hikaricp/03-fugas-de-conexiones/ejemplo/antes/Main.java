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
