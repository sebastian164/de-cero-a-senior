import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Solución del taller 08. Ejecutar con: java Main.java (termina con código 1 si alguna comprobación falla).
 * Cada comprobación arma su propio servicio con reloj fijo, sufijos fijos y dobles a mano: no hay estado
 * compartido entre ellas y el resultado no depende de la hora real ni del orden.
 */
public class Main {

    private static int fallos = 0;
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    public static void main(String[] args) {
        System.out.println("Versión nueva");
        fueraDeHorario();
        turnoDeManana();
        turnoDeTarde();
        numeracionConsecutiva();
        reglasPuras();
        ensambladoReal();

        System.out.println("Versión original: el contador static hace que el orden importe");
        demostrarDependenciaDelOrdenEnElOriginal();

        System.out.println(fallos == 0 ? "TODO PASA" : fallos + " FALLOS");
        System.exit(fallos == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ comprobaciones del punto 3

    static void fueraDeHorario() {
        for (String hora : List.of("06:59", "17:00", "23:30")) {
            Escenario e = new Escenario(hora);
            Optional<String> turno = e.servicio.asignar("Ana");
            comprobar("a las " + hora + " no se asigna turno", turno.isEmpty());
            comprobar("a las " + hora + " no hay efectos: ni pantalla ni SMS", e.pantalla.textos.isEmpty() && e.sms.mensajes.isEmpty());
        }
        Escenario e = new Escenario("06:59");
        e.servicio.asignar("Ana");
        e.reloj.ponerEn("07:00");
        comprobar("un intento fuera de horario no consume número", e.servicio.asignar("Luis").orElseThrow().equals("M1-a1b2"));
    }

    static void turnoDeManana() {
        for (String hora : List.of("07:00", "11:59")) {
            Escenario e = new Escenario(hora);
            comprobar("a las " + hora + " el turno es de mañana", e.servicio.asignar("Ana").orElseThrow().equals("M1-a1b2"));
            comprobar("a las " + hora + " se muestra en la pantalla", e.pantalla.textos.equals(List.of("Ana -> M1-a1b2")));
            comprobar("a las " + hora + " se envía el SMS", e.sms.mensajes.equals(List.of("Ana: Tu turno es M1-a1b2")));
        }
    }

    static void turnoDeTarde() {
        for (String hora : List.of("12:00", "16:59")) {
            Escenario e = new Escenario(hora);
            comprobar("a las " + hora + " el turno es de tarde", e.servicio.asignar("Ana").orElseThrow().equals("T1-a1b2"));
        }
    }

    static void numeracionConsecutiva() {
        Escenario e = new Escenario("11:58", "a1b2", "c3d4", "e5f6");
        String primero = e.servicio.asignar("Ana").orElseThrow();
        String segundo = e.servicio.asignar("Luis").orElseThrow();
        e.reloj.ponerEn("12:01");
        String tercero = e.servicio.asignar("Marta").orElseThrow();
        comprobar("la numeración es consecutiva y sigue al pasar a la tarde: " + List.of(primero, segundo, tercero),
                List.of(primero, segundo, tercero).equals(List.of("M1-a1b2", "M2-c3d4", "T3-e5f6")));
        comprobar("otro servicio empieza en 1: no hay estado global", new Escenario("08:00").servicio.asignar("Ana").orElseThrow().startsWith("M1-"));
    }

    static void reglasPuras() {
        comprobar("decisión pura: 06:59 fuera", ReglasDeTurno.jornadaPara(LocalTime.of(6, 59)).isEmpty());
        comprobar("decisión pura: 07:00 mañana", ReglasDeTurno.jornadaPara(LocalTime.of(7, 0)).orElseThrow() == Jornada.MANANA);
        comprobar("decisión pura: 12:00 tarde", ReglasDeTurno.jornadaPara(LocalTime.of(12, 0)).orElseThrow() == Jornada.TARDE);
        comprobar("formato puro", ReglasDeTurno.codigo(Jornada.TARDE, 14, "zz99").equals("T14-zz99"));
    }

    static void ensambladoReal() {
        // La raíz de composición de producción, con reloj y sufijos reales: solo se comprueba el formato.
        List<String> pantalla = new ArrayList<>();
        ServicioTurnos real = new ServicioTurnos(Clock.system(BOGOTA), new Numerador(), Sufijos.aleatorios(),
                pantalla::add, (paciente, texto) -> { });
        Optional<String> turno = real.asignar("Ana");
        comprobar("con el reloj real el turno, si lo hay, tiene el formato [MT]n-xxxx", turno.map(t -> t.matches("[MT]1-[0-9a-f]{4}")).orElse(pantalla.isEmpty()));
    }

    // ------------------------------------------------------------------ punto 4

    /**
     * La versión original lee la hora real. Para que la demostración no dependa de cuándo se ejecute,
     * se cambia la zona horaria por defecto de la JVM a una en la que ahora mismo son las 9 de la mañana.
     * Eso ya es una prueba de que la zona horaria es otra dependencia oculta.
     */
    static void demostrarDependenciaDelOrdenEnElOriginal() {
        TimeZone zonaOriginal = TimeZone.getDefault();
        int horaUtc = ZonedDateTime.now(ZoneOffset.UTC).getHour();
        int desfase = Math.floorMod(9 - horaUtc + 12, 24) - 12;   // entre -12 y +11
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.ofHours(desfase)));
        try {
            String primeraVez = pruebaOriginalPrimerTurnoEsM1();
            pruebaOriginalCualquieraQueAsigne();
            String despuesDeOtra = pruebaOriginalPrimerTurnoEsM1();
            System.out.println("   'el primer turno es M1' ejecutada primero:           " + primeraVez);
            System.out.println("   la misma prueba, después de otra que asignó un turno: " + despuesDeOtra);
            comprobar("en el original, la misma prueba pasa o falla según lo que corrió antes",
                    primeraVez.startsWith("PASA") && despuesDeOtra.startsWith("FALLA"));
        } finally {
            TimeZone.setDefault(zonaOriginal);
        }
    }

    static String pruebaOriginalPrimerTurnoEsM1() {
        String salida = capturarSalida(() -> new ServicioTurnosOriginal().asignar("Ana"));
        return salida.contains("[pantalla] Ana -> M1-") ? "PASA (" + primeraLinea(salida) + ")" : "FALLA (" + primeraLinea(salida) + ")";
    }

    static void pruebaOriginalCualquieraQueAsigne() {
        capturarSalida(() -> new ServicioTurnosOriginal().asignar("Luis"));
    }

    static String capturarSalida(Runnable accion) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            accion.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    static String primeraLinea(String texto) {
        return texto.lines().findFirst().orElse("");
    }

    // ------------------------------------------------------------------ utilidades

    static void comprobar(String descripcion, boolean ok) {
        System.out.println((ok ? "   PASA  " : "   FALLA ") + descripcion);
        if (!ok) {
            fallos++;
        }
    }

    /** Un servicio armado con dobles a mano. Cada comprobación crea el suyo. */
    static final class Escenario {
        final RelojAjustable reloj;
        final PantallaEspia pantalla = new PantallaEspia();
        final SmsEspia sms = new SmsEspia();
        final ServicioTurnos servicio;

        Escenario(String hora, String... sufijos) {
            reloj = new RelojAjustable(hora);
            List<String> lista = sufijos.length == 0 ? List.of("a1b2") : List.of(sufijos);
            AtomicInteger siguiente = new AtomicInteger();
            Supplier<String> sufijosFijos = () -> lista.get(Math.min(siguiente.getAndIncrement(), lista.size() - 1));
            servicio = new ServicioTurnos(reloj, new Numerador(), sufijosFijos, pantalla, sms);
        }
    }

    static final class RelojAjustable extends Clock {
        private Instant instante;

        RelojAjustable(String hora) { ponerEn(hora); }

        void ponerEn(String hora) {
            instante = ZonedDateTime.of(2026, 3, 2, 0, 0, 0, 0, BOGOTA).with(LocalTime.parse(hora)).toInstant();
        }

        @Override public ZoneId getZone() { return BOGOTA; }
        @Override public Clock withZone(ZoneId zona) { throw new UnsupportedOperationException(); }
        @Override public Instant instant() { return instante; }
    }

    static final class PantallaEspia implements Pantalla {
        final List<String> textos = new ArrayList<>();
        @Override public void mostrar(String texto) { textos.add(texto); }
    }

    static final class SmsEspia implements Mensajeria {
        final List<String> mensajes = new ArrayList<>();
        @Override public void enviar(String paciente, String texto) { mensajes.add(paciente + ": " + texto); }
    }
}

// ====================================================================== versión nueva

enum Jornada {
    MANANA("M"), TARDE("T");

    final String letra;

    Jornada(String letra) { this.letra = letra; }
}

/** Núcleo puro: decide con datos y devuelve datos. Ni reloj, ni azar, ni efectos. */
final class ReglasDeTurno {

    private static final LocalTime APERTURA = LocalTime.of(7, 0);
    private static final LocalTime MEDIODIA = LocalTime.of(12, 0);
    private static final LocalTime CIERRE = LocalTime.of(17, 0);

    private ReglasDeTurno() {
    }

    static Optional<Jornada> jornadaPara(LocalTime hora) {
        if (hora.isBefore(APERTURA) || !hora.isBefore(CIERRE)) {
            return Optional.empty();
        }
        return Optional.of(hora.isBefore(MEDIODIA) ? Jornada.MANANA : Jornada.TARDE);
    }

    static String codigo(Jornada jornada, int numero, String sufijo) {
        return jornada.letra + numero + "-" + sufijo;
    }
}

interface Pantalla {
    void mostrar(String texto);
}

interface Mensajeria {
    void enviar(String paciente, String texto);
}

/** El contador ya no es static: cada servicio (y cada prueba) tiene el suyo. */
final class Numerador {
    private final AtomicInteger ultimo = new AtomicInteger();

    int siguiente() { return ultimo.incrementAndGet(); }
}

final class Sufijos {
    private Sufijos() {
    }

    static Supplier<String> aleatorios() {
        return () -> UUID.randomUUID().toString().substring(0, 4);
    }
}

/** Cáscara imperativa: lee el reloj, pide el número y el sufijo, decide con el núcleo puro y produce los efectos. */
final class ServicioTurnos {

    private final Clock reloj;
    private final Numerador numerador;
    private final Supplier<String> sufijos;
    private final Pantalla pantalla;
    private final Mensajeria mensajeria;

    ServicioTurnos(Clock reloj, Numerador numerador, Supplier<String> sufijos, Pantalla pantalla, Mensajeria mensajeria) {
        this.reloj = reloj;
        this.numerador = numerador;
        this.sufijos = sufijos;
        this.pantalla = pantalla;
        this.mensajeria = mensajeria;
    }

    /** El turno asignado, o vacío si es fuera de horario. Quien llama decide cómo informarlo. */
    Optional<String> asignar(String paciente) {
        Optional<Jornada> jornada = ReglasDeTurno.jornadaPara(LocalTime.now(reloj));
        if (jornada.isEmpty()) {
            return Optional.empty();
        }
        String turno = ReglasDeTurno.codigo(jornada.get(), numerador.siguiente(), sufijos.get());
        pantalla.mostrar(paciente + " -> " + turno);
        mensajeria.enviar(paciente, "Tu turno es " + turno);
        return Optional.of(turno);
    }
}

// ====================================================================== original (taller/inicio), sin cambios salvo el nombre

class ServicioTurnosOriginal {

    private static int contador = 0;

    void asignar(String paciente) {
        java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
        if (ahora.getHour() < 7 || ahora.getHour() >= 17) {
            System.out.println("Fuera de horario");
            return;
        }
        contador++;
        String turno = (ahora.getHour() < 12 ? "M" : "T") + contador + "-" + UUID.randomUUID().toString().substring(0, 4);
        new PantallaSala().mostrar(paciente + " -> " + turno);
        new Sms().enviar(paciente, "Tu turno es " + turno);
    }
}

class PantallaSala { void mostrar(String texto) { System.out.println("[pantalla] " + texto); } }
class Sms { void enviar(String paciente, String texto) { System.out.println("[sms] " + texto); } }
