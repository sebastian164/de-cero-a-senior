package bicis;

import java.time.LocalDateTime;

/** Un alquiler de bicicleta: quién, cuál, desde dónde y desde cuándo. */
public record Alquiler(String id, String socio, String bicicleta, String estacionOrigen, LocalDateTime inicio,
        boolean abonado, boolean cerrado) {

    public static Alquiler iniciado(String id, String socio, String bicicleta, String estacionOrigen, LocalDateTime inicio) {
        return new Alquiler(id, socio, bicicleta, estacionOrigen, inicio, false, false);
    }

    public static Alquiler iniciadoPorAbonado(String id, String socio, String bicicleta, String estacionOrigen,
            LocalDateTime inicio) {
        return new Alquiler(id, socio, bicicleta, estacionOrigen, inicio, true, false);
    }

    public Alquiler cerrar() {
        return new Alquiler(id, socio, bicicleta, estacionOrigen, inicio, abonado, true);
    }
}
