import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class Pruebas {
    private static int fallos = 0, total = 0;

    public static void main(String[] args) {
        List<String> historial = new ArrayList<>();
        Clock reloj = Clock.fixed(Instant.parse("2026-03-15T15:00:00Z"), ZoneId.of("America/Bogota"));
        ServicioSuscripciones s = new ServicioSuscripciones((u, v) -> "C-" + v,
                (f, u, v, c) -> historial.add(f + ";" + u + ";" + v + ";" + c), reloj);
        igual("3 meses sin descuento", "Renovado hasta 2026-06-15 (C-90000)", s.renovar("ana", 3));
        igual("11 meses sin descuento", 330_000L, ServicioSuscripciones.valorPor(11));
        igual("12 meses con descuento", 288_000L, ServicioSuscripciones.valorPor(12));
        igual("24 meses con descuento", 576_000L, ServicioSuscripciones.valorPor(24));
        igual("historial con fecha del reloj", "2026-03-15;ana;90000;C-90000", historial.get(0));
        // La zona horaria también es una dependencia: 2026-03-01T02:00Z en Bogotá todavía es 28 de febrero.
        Clock madrugadaUtc = Clock.fixed(Instant.parse("2026-03-01T02:00:00Z"), ZoneId.of("America/Bogota"));
        igual("fecha en la zona del negocio", "Renovado hasta 2026-03-28 (C-30000)",
                new ServicioSuscripciones((u, v) -> "C-" + v, (f, u, v, c) -> { }, madrugadaUtc).renovar("ana", 1));
        total++;
        try { s.renovar("ana", 0); fallos++; } catch (IllegalArgumentException e) { }
        System.out.println(total + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }

    static void igual(String n, Object e, Object r) { total++; if (!e.equals(r)) { fallos++; System.out.println("FALLA " + n + ": " + e + " != " + r); } }
}
