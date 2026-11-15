public class Main {

    public static void main(String[] args) {
        Notificador basico = new NotificadorSms();

        // Se arma por capas, en el orden que se necesite.
        Notificador completo = new ConRegistro(new ConReintento(basico, 3));

        completo.enviar("3001234567", "Tu código es 4821");
    }
}

interface Notificador {
    void enviar(String destino, String mensaje);
}

class NotificadorSms implements Notificador {
    private int intentos = 0;

    @Override
    public void enviar(String destino, String mensaje) {
        intentos++;
        if (intentos < 3) {
            throw new IllegalStateException("Red no disponible (intento " + intentos + ")");
        }
        System.out.println("SMS a " + destino + ": " + mensaje);
    }
}

/** Un decorador ES un Notificador y TIENE un Notificador. */
class ConReintento implements Notificador {

    private final Notificador envuelto;
    private final int maximo;

    ConReintento(Notificador envuelto, int maximo) {
        this.envuelto = envuelto;
        this.maximo = maximo;
    }

    @Override
    public void enviar(String destino, String mensaje) {
        for (int intento = 1; ; intento++) {
            try {
                envuelto.enviar(destino, mensaje);
                return;
            } catch (IllegalStateException e) {
                if (intento == maximo) {
                    throw e;
                }
                System.out.println("  reintentando tras: " + e.getMessage());
            }
        }
    }
}

class ConRegistro implements Notificador {

    private final Notificador envuelto;

    ConRegistro(Notificador envuelto) {
        this.envuelto = envuelto;
    }

    @Override
    public void enviar(String destino, String mensaje) {
        System.out.println("[inicio] envío a " + destino);
        envuelto.enviar(destino, mensaje);
        System.out.println("[fin] envío a " + destino);
    }
}
