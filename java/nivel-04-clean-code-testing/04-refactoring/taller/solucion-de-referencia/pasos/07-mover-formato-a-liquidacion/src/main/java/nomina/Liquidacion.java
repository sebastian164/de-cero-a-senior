package nomina;

record Liquidacion(long base, long extra, long descuento) {

    long neto() {
        return base + extra - descuento;
    }

    String resumen() {
        return "Neto: " + neto();
    }

    String conDetalle() {
        return resumen() + " (base " + base + ", extra " + extra + ", descuento " + descuento + ")";
    }
}
