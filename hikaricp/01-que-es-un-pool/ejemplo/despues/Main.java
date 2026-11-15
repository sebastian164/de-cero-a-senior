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
