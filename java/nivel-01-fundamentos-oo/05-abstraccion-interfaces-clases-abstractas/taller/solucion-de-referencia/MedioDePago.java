/** Contrato común: lo único que todos los medios saben hacer. */
public interface MedioDePago {
    Comprobante pagar(long monto);
}
