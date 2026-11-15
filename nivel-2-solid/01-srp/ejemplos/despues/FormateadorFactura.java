/**
 * Razón para cambiar: cómo se ve la factura.
 * Quién pide el cambio: quien define la imagen ante el cliente.
 */
public class FormateadorFactura {

    private final CalculadoraImpuestos impuestos;

    public FormateadorFactura(CalculadoraImpuestos impuestos) {
        this.impuestos = impuestos;
    }

    public String formatear(Factura factura) {
        StringBuilder texto = new StringBuilder();
        texto.append("FACTURA ").append(factura.getNumero()).append("\n");
        texto.append("Cliente: ").append(factura.getCliente()).append("\n");
        texto.append("--------------------------------\n");
        for (Item item : factura.getItems()) {
            texto.append(item.getCantidad()).append(" x ")
                 .append(item.getDescripcion()).append("  $")
                 .append(item.importe()).append("\n");
        }
        texto.append("--------------------------------\n");
        texto.append("Subtotal: $").append(factura.subtotal()).append("\n");
        texto.append("IVA 19%:  $").append(impuestos.iva(factura)).append("\n");
        texto.append("TOTAL:    $").append(impuestos.total(factura)).append("\n");
        return texto.toString();
    }
}
