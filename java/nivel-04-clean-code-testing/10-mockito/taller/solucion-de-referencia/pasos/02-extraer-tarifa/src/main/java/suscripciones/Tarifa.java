package suscripciones;

/** La regla de precio, con nombres. Antes eran números sueltos dentro de renovar. */
final class Tarifa {

    private static final long VALOR_MENSUAL = 30_000;
    private static final int MESES_PARA_DESCUENTO = 12;
    private static final int PORCENTAJE_A_PAGAR_CON_DESCUENTO = 80;

    private Tarifa() {
    }

    static long valorPor(int meses) {
        long valor = meses * VALOR_MENSUAL;
        return meses >= MESES_PARA_DESCUENTO ? valor * PORCENTAJE_A_PAGAR_CON_DESCUENTO / 100 : valor;
    }
}
