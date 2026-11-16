package moderno;

/** 5. El mismo cálculo de comisión con Visitor clásico (jerarquía abierta, doble despacho). */
public final class ComisionVisitor {
    private ComisionVisitor() { }

    public interface Visitante<R> {
        R visitar(Tarjeta t);
        R visitar(Transferencia t);
        R visitar(Efectivo e);
        R visitar(Billetera b);
    }

    public interface Pago { <R> R aceptar(Visitante<R> v); }

    public record Tarjeta(FormaDePago.Franquicia franquicia, int cuotas) implements Pago {
        public <R> R aceptar(Visitante<R> v) { return v.visitar(this); }
    }
    public record Transferencia(boolean mismoBanco) implements Pago {
        public <R> R aceptar(Visitante<R> v) { return v.visitar(this); }
    }
    public record Efectivo() implements Pago {
        public <R> R aceptar(Visitante<R> v) { return v.visitar(this); }
    }
    public record Billetera() implements Pago {
        public <R> R aceptar(Visitante<R> v) { return v.visitar(this); }
    }

    public static class Comision implements Visitante<Long> {
        private final long monto;
        public Comision(long monto) { this.monto = monto; }

        public Long visitar(Tarjeta t) {
            if (t.franquicia() == FormaDePago.Franquicia.AMEX) return monto * 35 / 1000;
            return monto * 29 / 1000 + (t.cuotas() > 1 ? 500L * t.cuotas() : 0);
        }
        public Long visitar(Transferencia t) { return t.mismoBanco() ? 0L : 7_000L; }
        public Long visitar(Efectivo e) { return 3_500L; }
        public Long visitar(Billetera b) { return Math.max(1_000, monto * 15 / 1000); }
    }
}
