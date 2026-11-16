package tienda.dominio;

/** Un renglón del pedido: qué producto, cuántas unidades y a qué precio se vendió. Objeto de valor. */
public record LineaDePedido(String producto, int cantidad, Dinero precioUnitario) {

    public LineaDePedido {
        if (producto == null || producto.isBlank()) {
            throw new ReglaDeNegocioViolada("una línea necesita un producto");
        }
        if (cantidad < 1) {
            throw new ReglaDeNegocioViolada("la cantidad mínima de una línea es 1");
        }
        if (precioUnitario == null) {
            throw new ReglaDeNegocioViolada("una línea necesita un precio");
        }
    }

    public Dinero subtotal() {
        return precioUnitario.por(cantidad);
    }
}
