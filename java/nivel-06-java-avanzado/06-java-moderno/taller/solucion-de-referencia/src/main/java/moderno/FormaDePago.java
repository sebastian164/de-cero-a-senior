package moderno;

/** Formas de pago como tipo algebraico: interfaz sellada + records. Montos en pesos. */
public sealed interface FormaDePago permits FormaDePago.Tarjeta, FormaDePago.Transferencia, FormaDePago.Efectivo, FormaDePago.Billetera {

    enum Franquicia { VISA, MASTERCARD, AMEX }

    record Tarjeta(Franquicia franquicia, String ultimosCuatro, int cuotas) implements FormaDePago { }
    record Transferencia(String banco, boolean mismoBanco) implements FormaDePago { }
    record Efectivo(String puntoDeRecaudo) implements FormaDePago { }
    record Billetera(String proveedor, String telefono) implements FormaDePago { }

    /** Comisión con switch exhaustivo: sin default. */
    static long comision(FormaDePago forma, long monto) {
        return switch (forma) {
            case Tarjeta(Franquicia f, String ultimos, int cuotas) when f == Franquicia.AMEX -> monto * 35 / 1000;
            case Tarjeta(Franquicia f, String ultimos, int cuotas) when cuotas > 1 -> monto * 29 / 1000 + 500L * cuotas;
            case Tarjeta t -> monto * 29 / 1000;
            case Transferencia(String banco, boolean mismoBanco) -> mismoBanco ? 0 : 7_000;
            case Efectivo e -> 3_500;
            case Billetera b -> Math.max(1_000, monto * 15 / 1000);
        };
    }
}
