import java.time.LocalDate;
import java.util.Random;

public class Main {

    public static void main(String[] args) {
        new ServicioCupones().generarCuponDeCumpleanos("Ana", LocalDate.of(1990, LocalDate.now().getMonthValue(), 1));
    }
}

class ServicioCupones {

    void generarCuponDeCumpleanos(String cliente, LocalDate nacimiento) {
        LocalDate hoy = LocalDate.now();
        if (nacimiento.getMonth() == hoy.getMonth()) {
            String codigo = "CUMPLE-" + new Random().nextInt(10_000);
            int descuento = hoy.getDayOfMonth() <= 15 ? 20 : 10;
            new CorreoSmtp().enviar(cliente, "Tu cupón " + codigo + " de " + descuento + "%");
        }
    }
}

class CorreoSmtp {
    void enviar(String destino, String mensaje) {
        System.out.println("[SMTP real] a " + destino + ": " + mensaje);
    }
}
