import java.time.LocalDateTime;
import java.util.UUID;

public class Main {

    public static void main(String[] args) {
        new ServicioTurnos().asignar("Ana");
    }
}

class ServicioTurnos {

    private static int contador = 0;

    void asignar(String paciente) {
        LocalDateTime ahora = LocalDateTime.now();
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
