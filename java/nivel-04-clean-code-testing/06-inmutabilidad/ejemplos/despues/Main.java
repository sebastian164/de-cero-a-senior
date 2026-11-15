import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<String> invitados = new ArrayList<>(List.of("Ana", "Luis"));
        Evento evento = new Evento("Lanzamiento", LocalDate.of(2026, 3, 1), invitados);

        invitados.add("Colado");                 // ya no afecta al evento
        try {
            evento.invitados().add("Otro");      // falla: lista no modificable
        } catch (UnsupportedOperationException e) {
            System.out.println("No se puede modificar la lista de invitados");
        }

        Evento aplazado = evento.aplazadoA(LocalDate.of(2026, 4, 15));
        Evento conMarta = aplazado.conInvitado("Marta");

        System.out.println(evento);
        System.out.println(conMarta);

        Dinero precio = Dinero.pesos(120_000);
        System.out.println(precio.mas(Dinero.pesos(30_000)).conDescuento(10));
    }
}

record Evento(String nombre, LocalDate fecha, List<String> invitados) {

    /** Constructor compacto: valida y hace la copia defensiva. */
    Evento {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El evento necesita nombre");
        }
        invitados = List.copyOf(invitados);
    }

    Evento aplazadoA(LocalDate nuevaFecha) {
        return new Evento(nombre, nuevaFecha, invitados);
    }

    Evento conInvitado(String invitado) {
        List<String> nuevos = new ArrayList<>(invitados);
        nuevos.add(invitado);
        return new Evento(nombre, fecha, nuevos);
    }
}

/** Objeto de valor: se define por lo que vale, no por una identidad. */
record Dinero(long pesos) {

    Dinero {
        if (pesos < 0) {
            throw new IllegalArgumentException("El valor no puede ser negativo");
        }
    }

    static Dinero pesos(long valor) {
        return new Dinero(valor);
    }

    Dinero mas(Dinero otro) {
        return new Dinero(pesos + otro.pesos);
    }

    Dinero conDescuento(int porcentaje) {
        return new Dinero(pesos - pesos * porcentaje / 100);
    }
}
