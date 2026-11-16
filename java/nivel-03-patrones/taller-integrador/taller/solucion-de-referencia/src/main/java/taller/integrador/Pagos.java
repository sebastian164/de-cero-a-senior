package taller.integrador;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Requisito 4: pasarelas externas distintas por país, cada una con su API. Adapter: un puerto propio
 * (PasarelaPago) y un adaptador por SDK. La elección por país es un registro (mapa), no un switch.
 */
final class Pagos {
    private Pagos() { }

    record ResultadoPago(boolean aprobado, String referencia, String motivo) {
        static ResultadoPago aprobado(String ref) { return new ResultadoPago(true, ref, ""); }
        static ResultadoPago rechazado(String motivo) { return new ResultadoPago(false, "", motivo); }
    }

    /** El puerto: en pesos/moneda local entera y con resultado del dominio. */
    interface PasarelaPago {
        ResultadoPago cobrar(String estudianteId, long monto, String moneda);
    }

    // ---------- SDK ajenos simulados (no se pueden modificar) ----------

    /** Colombia: trabaja en centavos y responde códigos numéricos. */
    static final class SdkPagosCo {
        final List<String> llamadas = new java.util.ArrayList<>();
        int transaccion(String payer, long centavos, String currency) {
            llamadas.add(payer + " " + centavos + " " + currency);
            return centavos > 100_000_000_00L ? 51 : 0;     // 51 = fondos insuficientes
        }
    }

    /** México: recibe y devuelve mapas. */
    static final class SdkPagosMx {
        Map<String, Object> charge(Map<String, Object> request) {
            Map<String, Object> r = new HashMap<>();
            long amount = (Long) request.get("amount");
            r.put("status", amount > 50_000 ? "rejected" : "approved");
            r.put("id", "MX-" + amount);
            r.put("status_detail", amount > 50_000 ? "cc_rejected_high_risk" : "accredited");
            return r;
        }
    }

    /** Parte 3, cambio 1: pasarela nueva (EE. UU.). Lanza excepciones propias. */
    static final class SdkPagosUs {
        static final class CardDeclinedException extends Exception {
            CardDeclinedException(String m) { super(m); }
        }
        String createCharge(String customer, long cents) throws CardDeclinedException {
            if (cents > 50_000_000) throw new CardDeclinedException("card_declined");
            return "ch_" + customer + "_" + cents;
        }
    }

    // ---------- Adaptadores ----------

    static final class AdaptadorCo implements PasarelaPago {
        private final SdkPagosCo sdk;
        AdaptadorCo(SdkPagosCo sdk) { this.sdk = sdk; }
        public ResultadoPago cobrar(String est, long monto, String moneda) {
            int codigo = sdk.transaccion(est, monto * 100, moneda);
            return codigo == 0 ? ResultadoPago.aprobado("CO-" + est + "-" + monto)
                    : ResultadoPago.rechazado(codigo == 51 ? "Fondos insuficientes" : "Error " + codigo);
        }
    }

    static final class AdaptadorMx implements PasarelaPago {
        private final SdkPagosMx sdk;
        AdaptadorMx(SdkPagosMx sdk) { this.sdk = sdk; }
        public ResultadoPago cobrar(String est, long monto, String moneda) {
            Map<String, Object> r = sdk.charge(Map.of("payer", est, "amount", monto, "currency", moneda));
            return "approved".equals(r.get("status")) ? ResultadoPago.aprobado((String) r.get("id"))
                    : ResultadoPago.rechazado("Rechazado por riesgo");
        }
    }

    static final class AdaptadorUs implements PasarelaPago {
        private final SdkPagosUs sdk;
        AdaptadorUs(SdkPagosUs sdk) { this.sdk = sdk; }
        public ResultadoPago cobrar(String est, long monto, String moneda) {
            try {
                return ResultadoPago.aprobado(sdk.createCharge(est, monto * 100));
            } catch (SdkPagosUs.CardDeclinedException e) {
                return ResultadoPago.rechazado("Tarjeta rechazada");
            }
        }
    }

    /** Selección por país: registro. */
    static final class PasarelasPorPais {
        private final Map<String, PasarelaPago> pasarelas = new HashMap<>();
        private final Map<String, String> monedas = new HashMap<>();

        PasarelasPorPais registrar(String pais, String moneda, PasarelaPago p) {
            pasarelas.put(pais, p);
            monedas.put(pais, moneda);
            return this;
        }

        ResultadoPago cobrar(String pais, String estudianteId, long monto) {
            PasarelaPago p = pasarelas.get(pais);
            if (p == null) return ResultadoPago.rechazado("No hay pasarela para " + pais);
            return p.cobrar(estudianteId, monto, monedas.get(pais));
        }
    }
}
