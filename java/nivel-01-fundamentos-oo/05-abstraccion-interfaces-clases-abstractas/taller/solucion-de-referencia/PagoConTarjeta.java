/**
 * Clase abstracta: crédito y débito comparten estado (últimos cuatro dígitos)
 * y una regla (16 dígitos). Una interfaz no puede guardar ese estado.
 */
public abstract class PagoConTarjeta implements MedioDePago {

    private final String ultimosCuatro;

    protected PagoConTarjeta(String numero) {
        if (numero == null || !numero.matches("\\d{16}")) {
            throw new IllegalArgumentException("El número de tarjeta debe tener 16 dígitos");
        }
        this.ultimosCuatro = numero.substring(12);
    }

    @Override
    public final Comprobante pagar(long monto) {
        Montos.validar(monto);
        long cobrado = monto + comision(monto);
        return new Comprobante(cobrado, tipo() + " terminada en " + ultimosCuatro);
    }

    protected abstract long comision(long monto);

    protected abstract String tipo();

    public String ultimosCuatro() { return ultimosCuatro; }
}
