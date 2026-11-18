package banco.cuentas;

public record Cuenta(String id, String titular, long saldoCentavos, long version) {
}
