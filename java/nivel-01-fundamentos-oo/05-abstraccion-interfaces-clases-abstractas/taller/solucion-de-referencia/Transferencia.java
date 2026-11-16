import java.util.Set;

public final class Transferencia implements MedioDePago {

    private final String banco;

    public Transferencia(String banco, Set<String> bancosPermitidos) {
        if (banco == null || !bancosPermitidos.contains(banco)) {
            throw new IllegalArgumentException("Banco no permitido: " + banco);
        }
        this.banco = banco;
    }

    @Override
    public Comprobante pagar(long monto) {
        Montos.validar(monto);
        return new Comprobante(monto, "Transferencia desde " + banco);
    }
}
