final class Montos {
    private Montos() { }
    static void validar(long monto) {
        if (monto <= 0) throw new IllegalArgumentException("El monto debe ser positivo");
    }
}
