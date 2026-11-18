package banco.cuentas;

/** Los cuerpos JSON que llegan por HTTP. */
public final class Peticiones {

    private Peticiones() {
    }

    public record Apertura(String titular, long centavos) {
    }

    public record Transferencia(String origen, String destino, long centavos) {
    }
}
