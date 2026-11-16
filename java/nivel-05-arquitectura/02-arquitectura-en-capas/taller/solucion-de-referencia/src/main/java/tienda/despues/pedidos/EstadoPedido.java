package tienda.despues.pedidos;

public enum EstadoPedido {
    BORRADOR(false), CONFIRMADO(true), DESPACHADO(true), PAGADO(false);

    private final boolean cuentaComoDeuda;

    EstadoPedido(boolean cuentaComoDeuda) { this.cuentaComoDeuda = cuentaComoDeuda; }

    public boolean cuentaComoDeuda() { return cuentaComoDeuda; }
}
