import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** La llamada externa ocurre con la conexión tomada. */
public class Main {

    static final Semaphore pool = new Semaphore(4, true);       // cuatro conexiones
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
        if (!pool.tryAcquire(500, TimeUnit.MILLISECONDS)) {
            sinConexion.incrementAndGet();                       // "Connection is not available"
            return;
        }
        try {
            Thread.sleep(10);                                    // consulta el pedido
            Thread.sleep(300);                                   // llama al servicio de pagos, con la conexión tomada
            Thread.sleep(10);                                    // guarda el resultado
            atendidas.incrementAndGet();
        } finally {
            pool.release();
        }
    }
}
