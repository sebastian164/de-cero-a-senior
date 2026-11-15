public class Main {

    public static void main(String[] args) {
        Canal sms = (destino, texto) -> System.out.println("[SMS " + destino + "] " + texto);
        Canal correo = (destino, texto) -> System.out.println("[Correo " + destino + "] " + texto);

        new Alerta(sms).enviar("3001234567", "Intento de acceso sospechoso");
        new Alerta(correo).enviar("ana@correo.example", "Intento de acceso sospechoso");
        new Recordatorio(sms).enviar("3001234567", "Tu cita es mañana a las 9:00");
    }
}

/** Dimensión 2 (la "implementación"): por dónde se envía. */
interface Canal {
    void transmitir(String destino, String texto);
}

/** Dimensión 1 (la "abstracción"): qué tipo de aviso es. Tiene un canal: ese es el puente. */
abstract class Aviso {

    private final Canal canal;

    protected Aviso(Canal canal) {
        this.canal = canal;
    }

    final void enviar(String destino, String mensaje) {
        canal.transmitir(destino, formatear(mensaje));
    }

    protected abstract String formatear(String mensaje);
}

class Alerta extends Aviso {
    Alerta(Canal canal) { super(canal); }
    @Override protected String formatear(String mensaje) { return "URGENTE: " + mensaje.toUpperCase(); }
}

class Recordatorio extends Aviso {
    Recordatorio(Canal canal) { super(canal); }
    @Override protected String formatear(String mensaje) { return "Recuerda: " + mensaje; }
}
