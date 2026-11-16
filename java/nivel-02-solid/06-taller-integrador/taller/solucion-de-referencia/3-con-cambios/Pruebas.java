import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Pruebas de los cinco cambios con dobles en memoria, y contra la base de datos simulada. */
public class Pruebas {
    private static int fallos = 0, total = 0;
    static final LocalDateTime T0 = LocalDateTime.of(2026, 5, 4, 9, 0);

    public static void main(String[] args) {
        for (RepositorioCitas repo : List.of(new CitasEnMemoria(), new CitasEnBaseDeDatos())) {
            String n = repo.getClass().getSimpleName() + ": ";
            NotificacionesEnMemoria avisos = new NotificacionesEnMemoria();
            AgendaCitas agenda = new AgendaCitas(repo, avisos);
            Dueno ana = new Dueno("Ana", "ana@correo.example");
            // Cambio 1
            igual(n + "peluquería 35000 sin requisitos", 35_000L, agenda.registrar("Copito", ana, TiposDeCita.PELUQUERIA, T0).precio());
            // Cambio 4
            agenda.registrar("Michi", ana, TiposDeCita.VACUNACION, T0);
            rechaza(n + "vacuna a los 30 días", () -> agenda.registrar("Michi", ana, TiposDeCita.VACUNACION, T0.plusDays(30)));
            igual(n + "vacuna a los 31 días", 45_000L, agenda.registrar("Michi", ana, TiposDeCita.VACUNACION, T0.plusDays(31)).precio());
            igual(n + "otra mascota no se afecta", 45_000L, agenda.registrar("Toby", ana, TiposDeCita.VACUNACION, T0.plusDays(1)).precio());
            // Regla original
            rechaza(n + "cirugía sin consulta", () -> agenda.registrar("Toby", ana, TiposDeCita.CIRUGIA, T0.plusDays(2)));
            // Cambio 5: Ana lleva 4 citas; la 5.ª y la 6.ª pagan completo, la 7.ª (más de cinco previas) tiene 10 %.
            igual(n + "quinta cita completa", 60_000L, agenda.registrar("Toby", ana, TiposDeCita.CONSULTA, T0.plusDays(3)).precio());
            igual(n + "sexta cita completa", 60_000L, agenda.registrar("Toby", ana, TiposDeCita.CONSULTA, T0.plusDays(4)).precio());
            igual(n + "séptima con 10 %", 54_000L, agenda.registrar("Toby", ana, TiposDeCita.CONSULTA, T0.plusDays(5)).precio());
            igual(n + "año siguiente sin descuento", 60_000L,
                    agenda.registrar("Toby", ana, TiposDeCita.CONSULTA, LocalDateTime.of(2027, 1, 10, 9, 0)).precio());
            igual(n + "otro dueño sin descuento", 60_000L,
                    agenda.registrar("Rex", new Dueno("Luis", "luis@correo.example"), TiposDeCita.CONSULTA, T0).precio());
        }
        // Cambio 2
        NotificacionesEnMemoria correo = new NotificacionesEnMemoria();
        NotificacionesEnMemoria whatsapp = new NotificacionesEnMemoria();
        AgendaCitas agenda = new AgendaCitas(new CitasEnMemoria(),
                new NotificadorSegunPreferencia(Map.of(CanalAviso.CORREO, correo, CanalAviso.WHATSAPP, whatsapp)));
        agenda.registrar("A", new Dueno("Ana", "ana@correo.example"), TiposDeCita.CONSULTA, T0);
        agenda.registrar("B", new Dueno("Luis", "luis@correo.example", "3001234567", CanalAviso.WHATSAPP), TiposDeCita.CONSULTA, T0);
        igual("correo por defecto", 1, correo.enviados().size());
        igual("whatsapp por preferencia", 1, whatsapp.enviados().size());
        total++;
        try { new Dueno("X", "x@y.z", null, CanalAviso.WHATSAPP); fallos++; } catch (IllegalArgumentException e) { }

        // Cambio 2 en la persistencia: ida y vuelta del dueño completo por cada adaptador.
        Path archivo;
        try { archivo = Files.createTempFile("citas", ".txt"); } catch (IOException e) { throw new UncheckedIOException(e); }
        Dueno luis = new Dueno("Luis", "luis@correo.example", "3001234567", CanalAviso.WHATSAPP);
        Dueno ana = new Dueno("Ana", "ana@correo.example");
        for (RepositorioCitas repo : List.of(new CitasEnMemoria(), new CitasEnArchivo(archivo), new CitasEnBaseDeDatos())) {
            String n = repo.getClass().getSimpleName() + ": ";
            Cita conWhatsApp = new Cita("Michi", luis, TiposDeCita.CONSULTA, T0, 60_000);
            Cita conCorreo = new Cita("Copito", ana, TiposDeCita.PELUQUERIA, T0.plusHours(1), 35_000);
            repo.guardar(conWhatsApp);
            repo.guardar(conCorreo);
            igual(n + "ida y vuelta con WhatsApp", List.of(conWhatsApp), repo.deMascota("Michi"));
            igual(n + "ida y vuelta con correo y sin teléfono", List.of(conCorreo), repo.deMascota("Copito"));
        }
        // Un archivo escrito antes del cambio 2 (6 campos) se sigue leyendo: el dueño recibe correo.
        try { Files.writeString(archivo, "Rex;Eva;eva@correo.example;CONSULTA;2026-05-04T09:00;60000\n"); } catch (IOException e) { throw new UncheckedIOException(e); }
        igual("archivo antiguo se lee con correo", new Dueno("Eva", "eva@correo.example"), new CitasEnArchivo(archivo).deMascota("Rex").get(0).dueno());
        try { Files.delete(archivo); } catch (IOException e) { throw new UncheckedIOException(e); }

        // Cambio 4: una vacuna ya agendada en el futuro también cuenta (distancia en ambos sentidos).
        AgendaCitas agendaVacunas = new AgendaCitas(new CitasEnMemoria(), new NotificacionesEnMemoria());
        agendaVacunas.registrar("Michi", ana, TiposDeCita.VACUNACION, LocalDateTime.of(2026, 6, 10, 9, 0));
        rechaza("vacuna 5 días antes de otra ya agendada", () -> agendaVacunas.registrar("Michi", ana, TiposDeCita.VACUNACION, LocalDateTime.of(2026, 6, 5, 9, 0)));
        igual("vacuna 31 días antes de otra ya agendada", 45_000L,
                agendaVacunas.registrar("Michi", ana, TiposDeCita.VACUNACION, LocalDateTime.of(2026, 5, 10, 9, 0)).precio());
        System.out.println(total + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }

    static void igual(String n, Object e, Object r) { total++; if (!e.equals(r)) { fallos++; System.out.println("FALLA " + n + ": " + e + " != " + r); } }

    static void rechaza(String n, Runnable a) {
        total++;
        try { a.run(); fallos++; System.out.println("FALLA " + n + ": no rechazó"); } catch (CitaRechazada e) { }
    }
}
