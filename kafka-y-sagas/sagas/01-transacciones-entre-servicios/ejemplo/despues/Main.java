import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** La misma operación como saga: cada paso trae su compensación. */
public class Main {

    record Paso(String nombre, Runnable accion, Runnable compensacion) {}

    static final Set<String> vuelos = new LinkedHashSet<>();
    static final Set<String> hoteles = new LinkedHashSet<>();
    static final Set<String> cobros = new LinkedHashSet<>();

    public static void main(String[] args) {
        reservar("viaje-1", 800);
        reservar("viaje-2", 5000);

        System.out.println();
        System.out.println("Vuelos reservados:  " + vuelos);
        System.out.println("Hoteles reservados: " + hoteles);
        System.out.println("Cobros:             " + cobros);
    }

    static void reservar(String viaje, int valor) {
        System.out.println("Reservando " + viaje);
        List<Paso> pasos = List.of(
                new Paso("vuelo", () -> reservarVuelo(viaje), () -> cancelarVuelo(viaje)),
                new Paso("hotel", () -> reservarHotel(viaje), () -> cancelarHotel(viaje)),
                new Paso("pago", () -> cobrar(viaje, valor), () -> reembolsar(viaje)));

        Deque<Paso> completados = new ArrayDeque<>();
        try {
            for (Paso paso : pasos) {
                paso.accion().run();
                completados.push(paso);
            }
            System.out.println("  " + viaje + " confirmado");
        } catch (IllegalStateException error) {
            System.out.println("  " + viaje + " falló: " + error.getMessage() + ". Compensando en orden inverso:");
            while (!completados.isEmpty()) {
                completados.pop().compensacion().run();
            }
            System.out.println("  " + viaje + " cancelado");
        }
    }

    static void reservarVuelo(String viaje) {
        vuelos.add(viaje);
        System.out.println("  vuelo reservado");
    }

    static void cancelarVuelo(String viaje) {
        vuelos.remove(viaje);
        System.out.println("  vuelo cancelado");
    }

    static void reservarHotel(String viaje) {
        hoteles.add(viaje);
        System.out.println("  hotel reservado");
    }

    static void cancelarHotel(String viaje) {
        hoteles.remove(viaje);
        System.out.println("  hotel cancelado");
    }

    static void cobrar(String viaje, int valor) {
        if (valor > 1000) {
            throw new IllegalStateException("pago rechazado");
        }
        cobros.add(viaje);
        System.out.println("  pago cobrado");
    }

    static void reembolsar(String viaje) {
        cobros.remove(viaje);
        System.out.println("  pago reembolsado");
    }
}
