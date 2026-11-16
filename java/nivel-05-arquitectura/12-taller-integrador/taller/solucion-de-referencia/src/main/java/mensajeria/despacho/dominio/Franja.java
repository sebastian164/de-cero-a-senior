package mensajeria.despacho.dominio;

import java.time.LocalDateTime;

public record Franja(LocalDateTime desde, LocalDateTime hasta) {
    public Franja { if (!hasta.isAfter(desde)) throw new IllegalArgumentException("franja inválida"); }
    public boolean seSolapaCon(Franja o) { return desde.isBefore(o.hasta) && o.desde.isBefore(hasta); }
    public boolean esHoraPico() {
        int h = desde.getHour();
        return (h >= 7 && h < 9) || (h >= 17 && h < 19);
    }
}
