import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Raíz de composición: el único lugar que conoce ACME, la ruta del log, la clave y el reloj real. */
public class Main {

    public static void main(String[] args) {
        String clave = System.getenv().getOrDefault("ACME_CLAVE", "clave-secreta-prod");
        ServicioSuscripciones servicio = new ServicioSuscripciones(
                new CobrosConAcme(new PasarelaPagosAcme(clave)),
                new HistorialEnArchivo(new ArchivoLog("/var/log/suscripciones.log")),
                Clock.systemDefaultZone());
        System.out.println(servicio.renovar("ana", 3));

        // Verificaciones sin infraestructura real.
        List<Long> cobrados = new ArrayList<>();
        Cobros cobrosFalsos = (usuario, valor) -> { cobrados.add(valor); return "PRUEBA-1"; };
        HistorialRenovaciones historialFalso = (fecha, usuario, valor, codigo) -> { };
        Clock relojFijo = Clock.fixed(Instant.parse("2026-01-31T12:00:00Z"), ZoneId.of("America/Bogota"));
        ServicioSuscripciones paraProbar = new ServicioSuscripciones(cobrosFalsos, historialFalso, relojFijo);

        paraProbar.renovar("ana", 12);
        verificar("12 meses aplican 20 % de descuento", cobrados.get(0) == 288_000L);

        String resultado = paraProbar.renovar("ana", 1);
        verificar("31 de enero + 1 mes vence el 28 de febrero",
                resultado.equals("Renovado hasta " + LocalDate.of(2026, 2, 28) + " (PRUEBA-1)"));
    }

    private static void verificar(String nombre, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLA ") + nombre);
        if (!ok) System.exit(1);
    }
}
