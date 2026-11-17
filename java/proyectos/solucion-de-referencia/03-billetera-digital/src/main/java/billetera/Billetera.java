package billetera;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Núcleo de la billetera. Todo lo que depende del tiempo usa el {@link Clock} recibido. */
public class Billetera {
    public static final BigDecimal TOPE_DIARIO = new BigDecimal("1000.00");
    public static final BigDecimal COMISION_RETIRO = new BigDecimal("0.01");
    public static final BigDecimal COMISION_TRANSFERENCIA = new BigDecimal("0.005");
    public static final int INTENTOS_MAXIMOS = 3;

    public enum Tipo { DEPOSITO, RETIRO, COMISION, TRANSFERENCIA_ENVIADA, TRANSFERENCIA_RECIBIDA }

    public record Movimiento(Tipo tipo, BigDecimal monto, Instant cuando) {}

    public static class ErrorDeDominio extends RuntimeException {
        public ErrorDeDominio(String m) { super(m); }
    }
    public static class SaldoInsuficiente extends ErrorDeDominio {
        public SaldoInsuficiente() { super("Saldo insuficiente"); }
    }
    public static class TopeDiarioExcedido extends ErrorDeDominio {
        public TopeDiarioExcedido() { super("Tope diario excedido"); }
    }
    public static class CuentaBloqueada extends ErrorDeDominio {
        public CuentaBloqueada() { super("Cuenta bloqueada por intentos fallidos"); }
    }

    public static class Cuenta {
        private final String id;
        private final String pin;
        private final Clock reloj;
        private BigDecimal saldo = BigDecimal.ZERO;
        private final List<Movimiento> historial = new ArrayList<>();
        private int fallidos;
        private boolean bloqueada;

        public Cuenta(String id, String pin, Clock reloj) { this.id = id; this.pin = pin; this.reloj = reloj; }

        public String id() { return id; }
        public BigDecimal saldo() { return saldo; }
        public boolean bloqueada() { return bloqueada; }
        /** Copia inmutable: modificarla lanza UnsupportedOperationException y no altera la cuenta. */
        public List<Movimiento> historial() { return List.copyOf(historial); }

        public void depositar(BigDecimal monto) {
            positivo(monto);
            saldo = saldo.add(monto);
            registrar(Tipo.DEPOSITO, monto);
        }

        public void retirar(String pinIngresado, BigDecimal monto) {
            positivo(monto);
            autenticar(pinIngresado);
            BigDecimal comision = comision(monto, COMISION_RETIRO);
            exigirTopeYSaldo(monto, comision);
            saldo = saldo.subtract(monto).subtract(comision);
            registrar(Tipo.RETIRO, monto);
            registrar(Tipo.COMISION, comision);
        }

        public void transferir(String pinIngresado, Cuenta destino, BigDecimal monto) {
            positivo(monto);
            if (destino == this) throw new ErrorDeDominio("No se puede transferir a la misma cuenta");
            autenticar(pinIngresado);
            BigDecimal comision = comision(monto, COMISION_TRANSFERENCIA);
            exigirTopeYSaldo(monto, comision);
            saldo = saldo.subtract(monto).subtract(comision);
            registrar(Tipo.TRANSFERENCIA_ENVIADA, monto);
            registrar(Tipo.COMISION, comision);
            destino.saldo = destino.saldo.add(monto);
            destino.registrar(Tipo.TRANSFERENCIA_RECIBIDA, monto);
        }

        public void desbloquear() { bloqueada = false; fallidos = 0; }

        private void autenticar(String ingresado) {
            if (bloqueada) throw new CuentaBloqueada();
            if (!pin.equals(ingresado)) {
                if (++fallidos >= INTENTOS_MAXIMOS) bloqueada = true;
                throw new ErrorDeDominio("PIN incorrecto");
            }
            fallidos = 0;
        }

        private void exigirTopeYSaldo(BigDecimal monto, BigDecimal comision) {
            if (saldo.compareTo(monto.add(comision)) < 0) throw new SaldoInsuficiente();
            if (salidoHoy().add(monto).compareTo(TOPE_DIARIO) > 0) throw new TopeDiarioExcedido();
        }

        private BigDecimal salidoHoy() {
            LocalDate hoy = LocalDate.now(reloj);
            return historial.stream()
                    .filter(m -> (m.tipo() == Tipo.RETIRO || m.tipo() == Tipo.TRANSFERENCIA_ENVIADA)
                            && LocalDate.ofInstant(m.cuando(), reloj.getZone()).equals(hoy))
                    .map(Movimiento::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private static BigDecimal comision(BigDecimal monto, BigDecimal tasa) {
            return monto.multiply(tasa).setScale(2, java.math.RoundingMode.HALF_UP);
        }

        private static void positivo(BigDecimal monto) {
            if (monto == null || monto.signum() <= 0) throw new ErrorDeDominio("El monto debe ser positivo");
        }

        private void registrar(Tipo tipo, BigDecimal monto) { historial.add(new Movimiento(tipo, monto, reloj.instant())); }
    }
}
