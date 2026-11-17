package tienda.pedidos.interno;

import org.springframework.stereotype.Component;

/** Inyección de fallos para demostrar la consistencia: lanza en el paso indicado. */
@Component
public class Fallos {
    public volatile String pasoQueFalla;

    public void en(String paso) {
        if (paso.equals(pasoQueFalla)) throw new IllegalStateException("Fallo inyectado en el paso " + paso);
    }
}
