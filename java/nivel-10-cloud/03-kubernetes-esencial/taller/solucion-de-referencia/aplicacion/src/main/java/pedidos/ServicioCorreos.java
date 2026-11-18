package pedidos;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Simula una operación lenta (enviar un correo a un proveedor externo). */
@Service
public class ServicioCorreos {
    private final AtomicInteger enviados = new AtomicInteger();

    public void enviar(long demoraMs) {
        try {
            Thread.sleep(demoraMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        enviados.incrementAndGet();
    }

    @Async
    public void enviarEnCola(long demoraMs) {
        enviar(demoraMs);
    }

    public int enviados() {
        return enviados.get();
    }
}
