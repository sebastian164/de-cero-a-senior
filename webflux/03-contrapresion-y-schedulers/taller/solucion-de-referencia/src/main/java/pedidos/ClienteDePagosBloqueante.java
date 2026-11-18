package pedidos;

/** Cliente bloqueante simulado: un método normal que duerme el hilo que lo llama. */
public final class ClienteDePagosBloqueante {
    public Cobro cobrar(Pedido p) {
        try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        return new Cobro(p.id(), p.monto());
    }
}
