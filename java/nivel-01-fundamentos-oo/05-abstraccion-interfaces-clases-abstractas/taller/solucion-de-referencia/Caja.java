public final class Caja {
    /** Punto 3: funciona con cualquier medio sin conocerlo. */
    public Comprobante cobrar(MedioDePago medio, long monto) {
        Comprobante comprobante = medio.pagar(monto);
        System.out.println("Cobrado " + comprobante.valorCobrado() + " - " + comprobante.descripcion());
        return comprobante;
    }
}
