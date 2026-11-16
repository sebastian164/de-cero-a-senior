/** Acoplamiento temporal: hay que llamar configurar() y conectar() antes de enviar(), y nada lo obliga. */
public class Main {
    public static void main(String[] args) {
        ClienteCorreo bien = new ClienteCorreo();
        bien.configurar("smtp.ejemplo.test", 25);
        bien.conectar();
        bien.enviar("ana@correo.example", "Hola");

        ClienteCorreo mal = new ClienteCorreo();
        try {
            mal.enviar("ana@correo.example", "Hola");   // compila y falla en ejecución
        } catch (IllegalStateException e) {
            System.out.println("Orden incorrecto detectado tarde: " + e.getMessage());
        }
    }
}

class ClienteCorreo {
    private String servidor;
    private int puerto;
    private boolean conectado;

    void configurar(String servidor, int puerto) { this.servidor = servidor; this.puerto = puerto; }

    void conectar() {
        if (servidor == null) throw new IllegalStateException("Falta configurar el servidor");
        conectado = true;
    }

    void enviar(String para, String texto) {
        if (!conectado) throw new IllegalStateException("No hay conexión");
        System.out.println("Enviado a " + para + " por " + servidor + ":" + puerto + ": " + texto);
    }
}
