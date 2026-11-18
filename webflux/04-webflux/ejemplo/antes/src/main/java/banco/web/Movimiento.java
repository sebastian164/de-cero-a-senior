package banco.web;

public record Movimiento(String cuentaId, int renglon, long centavos, String concepto) {
}
