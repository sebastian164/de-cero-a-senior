package reservas.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record Franja(Instant inicio, Instant fin) {
    public Franja {
        if (inicio == null || fin == null || !fin.isAfter(inicio)) throw new IllegalArgumentException("La franja debe terminar después de empezar");
        if (Duration.between(inicio, fin).compareTo(Duration.ofHours(8)) > 0) throw new IllegalArgumentException("Una reserva dura como máximo 8 horas");
    }

    public boolean seSolapaCon(Franja otra) {
        return inicio.isBefore(otra.fin) && otra.inicio.isBefore(fin);
    }

    /** Un tramo libre dentro de una ventana de consulta (puede durar más que una reserva: no es una Franja reservable). */
    public record Hueco(Instant inicio, Instant fin) {}

    /** Los tramos de [desde, hasta) que no cubre ninguna de las franjas ocupadas. */
    public static List<Hueco> huecosLibres(Instant desde, Instant hasta, List<Franja> ocupadas) {
        if (!hasta.isAfter(desde)) throw new IllegalArgumentException("La ventana debe terminar después de empezar");
        List<Hueco> libres = new ArrayList<>();
        Instant cursor = desde;
        for (Franja o : ocupadas.stream().filter(f -> f.fin.isAfter(desde) && f.inicio.isBefore(hasta)).sorted(Comparator.comparing(Franja::inicio)).toList()) {
            if (o.inicio.isAfter(cursor)) libres.add(new Hueco(cursor, o.inicio));
            if (o.fin.isAfter(cursor)) cursor = o.fin;
        }
        if (hasta.isAfter(cursor)) libres.add(new Hueco(cursor, hasta));
        return libres;
    }
}
