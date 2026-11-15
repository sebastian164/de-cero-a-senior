/**
 * Razón para cambiar: los pasos del proceso de emitir una factura.
 * No calcula, no formatea, no guarda y no envía: coordina a quienes sí.
 */
public class ServicioFacturacion {

    private final FormateadorFactura formateador;
    private final ArchivoFacturas archivo;
    private final NotificadorCorreo notificador;

    public ServicioFacturacion(FormateadorFactura formateador,
                               ArchivoFacturas archivo,
                               NotificadorCorreo notificador) {
        this.formateador = formateador;
        this.archivo = archivo;
        this.notificador = notificador;
    }

    public void emitir(Factura factura) {
        String texto = formateador.formatear(factura);
        archivo.guardar(factura.getNumero(), texto);
        notificador.enviar(factura.getCorreoCliente(), "Su factura " + factura.getNumero(), texto);
    }
}
