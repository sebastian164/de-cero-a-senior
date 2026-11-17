import java.util.concurrent.atomic.AtomicInteger;

/** El servicio externo que cobra: tarda 100 ms y no sabe de transacciones ni de repeticiones. Cuenta los cobros. */
final class Pasarela {

    private final AtomicInteger cobros = new AtomicInteger();

    String cobrar(String cuenta, long centavos) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
        return "aprobado-" + cobros.incrementAndGet();
    }

    int cobros() {
        return cobros.get();
    }
}
