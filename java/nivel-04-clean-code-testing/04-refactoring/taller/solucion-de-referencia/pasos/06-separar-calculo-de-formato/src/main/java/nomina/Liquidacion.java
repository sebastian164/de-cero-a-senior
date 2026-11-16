package nomina;

record Liquidacion(long base, long extra, long descuento) {

    long neto() {
        return base + extra - descuento;
    }
}
