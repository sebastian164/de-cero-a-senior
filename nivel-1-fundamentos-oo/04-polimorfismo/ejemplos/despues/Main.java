import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<Canal> canales = List.of(
                new CanalCorreo("ana@correo.example"),
                new CanalSms("3001234567"),
                new CanalPush("dispositivo-77"));

        long costoTotal = 0;
        for (Canal canal : canales) {
            canal.enviar("Tu pedido fue enviado");
            costoTotal += canal.costo();
        }
        System.out.println("Costo total: " + costoTotal);

        // Sobrecarga: se decide al compilar, por el tipo de la variable.
        Object texto = "hola";
        imprimir(texto);
        imprimir("hola");
    }

    static void imprimir(Object valor) {
        System.out.println("versión Object: " + valor);
    }

    static void imprimir(String valor) {
        System.out.println("versión String: " + valor);
    }
}

interface Canal {
    void enviar(String mensaje);
    long costo();
}

class CanalCorreo implements Canal {

    private final String direccion;

    CanalCorreo(String direccion) {
        this.direccion = direccion;
    }

    @Override
    public void enviar(String mensaje) {
        System.out.println("[correo a " + direccion + "] " + mensaje);
    }

    @Override
    public long costo() {
        return 0;
    }
}

class CanalSms implements Canal {

    private static final int LONGITUD_MAXIMA = 20;
    private final String celular;

    CanalSms(String celular) {
        this.celular = celular;
    }

    @Override
    public void enviar(String mensaje) {
        String texto = mensaje.length() > LONGITUD_MAXIMA
                ? mensaje.substring(0, LONGITUD_MAXIMA) : mensaje;
        System.out.println("[sms a " + celular + "] " + texto);
    }

    @Override
    public long costo() {
        return 50;
    }
}

class CanalPush implements Canal {

    private final String dispositivo;

    CanalPush(String dispositivo) {
        this.dispositivo = dispositivo;
    }

    @Override
    public void enviar(String mensaje) {
        System.out.println("[push a " + dispositivo + "] " + mensaje);
    }

    @Override
    public long costo() {
        return 0;
    }
}
