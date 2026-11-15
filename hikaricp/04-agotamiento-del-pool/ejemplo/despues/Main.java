import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** La conexión se toma solo para hablar con la base; la llamada externa va sin ella. */
public class Main {

    static final Semaphore pool = new Semaphore(4, true);
    static final AtomicInteger atendidas = new AtomicInteger();
    static final AtomicInteger sinConexion = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        List<Callable<Void>> peticiones = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            peticiones.add(() -> {
                confirmarPedido();
                return null;
            });
        }
        ExecutorService hilos = Executors.newFixedThreadPool(12);
        hilos.invokeAll(peticiones);
        hilos.shutdown();

        System.out.println("Pool de 4 conexiones, 12 peticiones a la vez, espera máxima de 500 ms");
        System.out.println("Atendidas:             " + atendidas.get());
        System.out.println("Sin conexión a tiempo: " + sinConexion.get());
    }

    static void confirmarPedido() throws InterruptedException {
        if (!conConexion(10)) {                                  // 1. consulta el pedido y devuelve la conexión
            return;
        }
        Thread.sleep(300);                                       // 2. llama al servicio de pagos, sin conexión
        if (!conConexion(10)) {                                  // 3. guarda el resultado en otra transacción corta
            return;
        }
        atendidas.incrementAndGet();
    }

    /** Toma una conexión, trabaja con la base ese tiempo y la devuelve. */
    static boolean conConexion(long milisegundosDeTrabajo) throws InterruptedException {
        if (!pool.tryAcquire(500, TimeUnit.MILLISECONDS)) {
            sinConexion.incrementAndGet();
            return false;
        }
        try {
            Thread.sleep(milisegundosDeTrabajo);
            return true;
        } finally {
            pool.release();
        }
    }
}
