/**
 * Regla 3 en un único lugar: cuánto descuento recibe cada tipo de cliente.
 * Agregar un tipo (mayorista) es agregar una constante aquí; Pedido no se toca.
 */
public enum TipoCliente {
    REGULAR(0),
    VIP(10),
    EMPLEADO(30),
    MAYORISTA(15);

    private final int porcentajeDescuento;

    TipoCliente(int porcentajeDescuento) {
        this.porcentajeDescuento = porcentajeDescuento;
    }

    public long descuentoSobre(long subtotal) {
        return subtotal * porcentajeDescuento / 100;
    }
}
