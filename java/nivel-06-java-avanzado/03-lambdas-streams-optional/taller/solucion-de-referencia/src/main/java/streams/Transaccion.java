package streams;

import java.time.LocalDate;

public record Transaccion(String cliente, LocalDate fecha, Tipo tipo, long valor, String categoria) {
    public enum Tipo { COMPRA, DEVOLUCION, TRANSFERENCIA }
}
