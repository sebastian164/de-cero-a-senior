public class Main {

    public static void main(String[] args) {
        notificar("CORREO", "ana@correo.example", "Tu pedido fue enviado");
        notificar("SMS", "3001234567", "Tu pedido fue enviado");
        System.out.println("Costo SMS: " + costo("SMS"));
        System.out.println("Costo PUSH: " + costo("PUSH"));
    }

    static void notificar(String canal, String destino, String mensaje) {
        if (canal.equals("CORREO")) {
            System.out.println("[correo a " + destino + "] " + mensaje);
        } else if (canal.equals("SMS")) {
            String texto = mensaje.length() > 20 ? mensaje.substring(0, 20) : mensaje;
            System.out.println("[sms a " + destino + "] " + texto);
        } else if (canal.equals("PUSH")) {
            System.out.println("[push a " + destino + "] " + mensaje);
        }
    }

    static long costo(String canal) {
        if (canal.equals("CORREO")) {
            return 0;
        } else if (canal.equals("SMS")) {
            return 50;
        }
        return 0; // ¿PUSH es gratis o alguien olvidó agregarlo?
    }
}
