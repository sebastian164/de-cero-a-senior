import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Raíz de composición, y verificación de las reglas con dobles en memoria. */
public class Main {

    public static void main(String[] args) throws Exception {
        // 1. Verificación de reglas con dobles en memoria (sin archivos ni correo).
        CitasEnMemoria memoria = new CitasEnMemoria();
        NotificacionesEnMemoria avisos = new NotificacionesEnMemoria();
        AgendaCitas agendaDePrueba = new AgendaCitas(memoria, avisos);
        LocalDateTime hoy = LocalDateTime.of(2026, 5, 4, 9, 0);
        Dueno ana = new Dueno("Ana", "ana@correo.example");
        verificar("cirugía sin consulta se rechaza", rechaza(() -> agendaDePrueba.registrar("Firulais", ana, TiposDeCita.CIRUGIA, hoy)));
        agendaDePrueba.registrar("Firulais", ana, TiposDeCita.CONSULTA, hoy);
        verificar("cirugía con consulta previa cuesta 400000",
                agendaDePrueba.registrar("Firulais", ana, TiposDeCita.CIRUGIA, hoy.plusHours(2)).precio() == 400_000);
        verificar("consulta posterior no habilita cirugía",
                rechaza(() -> agendaDePrueba.registrar("Toby", ana, TiposDeCita.CIRUGIA, hoy.minusDays(1))));
        verificar("se avisó dos veces", avisos.enviados().size() == 2);
        verificar("reporte suma 460000", new ReporteDiario(memoria).generar(hoy.toLocalDate()).endsWith("Total facturado: 460000"));

        // 2. El sistema real: archivo de texto y correo (simulado por consola).
        Path archivo = Files.createTempFile("citas", ".txt");
        RepositorioCitas repositorio = new CitasEnArchivo(archivo);
        Notificador notificador = new NotificadorSegunPreferencia(Map.of(
                CanalAviso.CORREO, new NotificadorCorreo(), CanalAviso.WHATSAPP, new NotificadorWhatsApp()));
        AgendaCitas agenda = new AgendaCitas(repositorio, notificador);
        Dueno luis = new Dueno("Luis", "luis@correo.example", "3001234567", CanalAviso.WHATSAPP);
        agenda.registrar("Firulais", ana, TiposDeCita.CONSULTA, hoy);
        agenda.registrar("Firulais", ana, TiposDeCita.CIRUGIA, hoy.plusHours(2));
        agenda.registrar("Michi", luis, TiposDeCita.VACUNACION, hoy.plusHours(3));
        try {
            agenda.registrar("Michi", luis, TiposDeCita.CIRUGIA, hoy.plusHours(4));
        } catch (CitaRechazada e) {
            System.out.println("Rechazada: " + e.getMessage());
        }
        System.out.println(new ReporteDiario(repositorio).generar(hoy.toLocalDate()));
        System.out.println("Líneas en el archivo: " + Files.readAllLines(archivo).size());
        Files.delete(archivo);

        // 3. Cambio 3: la misma agenda sobre la base de datos simulada.
        RepositorioCitas baseDeDatos = new CitasEnBaseDeDatos();
        AgendaCitas agendaBd = new AgendaCitas(baseDeDatos, notificador);
        agendaBd.registrar("Copito", ana, TiposDeCita.PELUQUERIA, hoy);
        System.out.println(new ReporteDiario(baseDeDatos).generar(hoy.toLocalDate()));
    }

    static boolean rechaza(Runnable accion) {
        try { accion.run(); return false; } catch (CitaRechazada e) { return true; }
    }

    static void verificar(String nombre, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLA ") + nombre);
        if (!ok) System.exit(1);
    }
}
