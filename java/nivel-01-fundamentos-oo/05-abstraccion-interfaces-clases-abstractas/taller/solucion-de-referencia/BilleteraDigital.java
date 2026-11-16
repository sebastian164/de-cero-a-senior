/** Punto 5: medio nuevo. No se modificó ninguna clase existente. */
public final class BilleteraDigital implements MedioDePago {

    private final String celular;

    public BilleteraDigital(String celular) {
        if (celular == null || !celular.matches("3\\d{9}")) {
            throw new IllegalArgumentException("Celular inválido");
        }
        this.celular = celular;
    }

    @Override
    public Comprobante pagar(long monto) {
        Montos.validar(monto);
        return new Comprobante(monto, "Billetera digital " + celular.substring(0, 3) + "****" + celular.substring(6));
    }
}
