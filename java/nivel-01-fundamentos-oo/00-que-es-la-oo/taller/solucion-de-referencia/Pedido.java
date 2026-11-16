/**
 * Un pedido que existe es un pedido válido (regla 1) y solo cambia de estado
 * por sus propios métodos (regla 2). Las operaciones inválidas lanzan excepción (regla 4).
 */
public final class Pedido {

    private final String cliente;
    private final TipoCliente tipoCliente;
    private final long subtotal;
    private EstadoPedido estado = EstadoPedido.CREADO;

    public Pedido(String cliente, TipoCliente tipoCliente, long subtotal) {
        if (cliente == null || cliente.isBlank()) {
            throw new IllegalArgumentException("El pedido necesita un cliente");
        }
        if (tipoCliente == null) {
            throw new IllegalArgumentException("El pedido necesita un tipo de cliente");
        }
        if (subtotal < 0) {
            throw new IllegalArgumentException("El subtotal no puede ser negativo");
        }
        this.cliente = cliente;
        this.tipoCliente = tipoCliente;
        this.subtotal = subtotal;
    }

    public long total() {
        return subtotal - tipoCliente.descuentoSobre(subtotal);
    }

    public void pagar() {
        estado = estado.siguiente(EstadoPedido.PAGADO);
    }

    public void enviar() {
        estado = estado.siguiente(EstadoPedido.ENVIADO);
    }

    public EstadoPedido estado() {
        return estado;
    }

    public String cliente() {
        return cliente;
    }
}
