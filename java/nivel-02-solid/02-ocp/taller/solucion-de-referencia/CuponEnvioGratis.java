/** ENVIOGRATIS: anula el recargo de envío. Obligó a que los cupones vean el desglose (ver ANALISIS). */
public record CuponEnvioGratis(String codigo) implements Cupon {
    public long descuento(Desglose d) { return d.recargo(RecargoEnvio.NOMBRE); }
}
