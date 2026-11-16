package tienda.pedidos.dominio;

public class Pedido {
    public enum Estado { PENDIENTE, CONFIRMADO, PAGO_RECHAZADO }

    private final long id;
    private final Dinero total;
    private Estado estado = Estado.PENDIENTE;
    private String autorizacion;

    public Pedido(long id, Dinero total) {
        this.id = id;
        this.total = total;
    }

    public void registrarPago(ResultadoPago resultado) {
        if (estado == Estado.CONFIRMADO) throw new IllegalStateException("ya confirmado");
        switch (resultado) {
            case ResultadoPago.Aprobado(String codigo) -> { estado = Estado.CONFIRMADO; autorizacion = codigo; }
            case ResultadoPago.Rechazado r -> estado = Estado.PAGO_RECHAZADO;
        }
    }

    public long id() { return id; }
    public Dinero total() { return total; }
    public Estado estado() { return estado; }
    public String autorizacion() { return autorizacion; }
}
