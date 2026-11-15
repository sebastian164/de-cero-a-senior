import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/** Un "orquestador" que es un método: el plan y las compensaciones viven en la memoria del proceso. */
public class Main {

    static final Set<String> vuelos = new LinkedHashSet<>();
    static final Set<String> hoteles = new LinkedHashSet<>();
    static final Set<String> cobros = new LinkedHashSet<>();

    public static void main(String[] args) {
        try {
            reservar("viaje-3", 800, true);
        } catch (Error caida) {
            System.out.println("  >> " + caida.getMessage());
        }
        System.out.println("  >> el proceso reinicia. ¿En qué iba viaje-3? Nadie lo sabe.");

        System.out.println();
        System.out.println("Vuelos reservados:  " + vuelos);
        System.out.println("Hoteles reservados: " + hoteles);
        System.out.println("Cobros:             " + cobros);
    }

    static void reservar(String viaje, int valor, boolean caerseAMitad) {
        Deque<Runnable> compensaciones = new ArrayDeque<>();      // solo existe mientras el método corre
        try {
            vuelos.add(viaje);
            compensaciones.push(() -> vuelos.remove(viaje));
            System.out.println("vuelo reservado");

            hoteles.add(viaje);
            compensaciones.push(() -> hoteles.remove(viaje));
            System.out.println("hotel reservado");

            if (caerseAMitad) {
                throw new Error("el proceso se cayó después de reservar el hotel");   // simula un apagón
            }

            if (valor > 1000) {
                throw new IllegalStateException("pago rechazado");
            }
            cobros.add(viaje);
            System.out.println("pago cobrado");
        } catch (IllegalStateException error) {
            while (!compensaciones.isEmpty()) {
                compensaciones.pop().run();
            }
        }
    }
}
