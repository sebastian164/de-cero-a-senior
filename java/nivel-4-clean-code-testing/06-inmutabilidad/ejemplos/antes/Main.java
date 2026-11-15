import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<String> invitados = new ArrayList<>(List.of("Ana", "Luis"));
        Date fecha = new Date(0);
        Evento evento = new Evento("Lanzamiento", fecha, invitados);

        // Nadie llamó a un método de Evento, y el evento cambió.
        invitados.add("Colado");
        fecha.setTime(86_400_000L * 365);
        evento.getInvitados().clear();

        System.out.println(evento.getFecha() + " " + evento.getInvitados());
    }
}

class Evento {

    private final String nombre;
    private final Date fecha;
    private final List<String> invitados;

    Evento(String nombre, Date fecha, List<String> invitados) {
        this.nombre = nombre;
        this.fecha = fecha;
        this.invitados = invitados;
    }

    String getNombre() { return nombre; }
    Date getFecha() { return fecha; }
    List<String> getInvitados() { return invitados; }
}
