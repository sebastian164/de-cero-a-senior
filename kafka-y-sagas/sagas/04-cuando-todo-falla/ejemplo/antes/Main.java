import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Participantes que suponen que cada mensaje llega una vez, en orden, y que cancelar nunca falla. */
public class Main {

    record Mensaje(String id, String tipo, String saga) {}

    static final Map<String, Integer> vecesCobrado = new LinkedHashMap<>();
    static final Set<String> hoteles = new LinkedHashSet<>();
    static int llamadasAlServicioDeHoteles = 0;

    public static void main(String[] args) {
        // 1. Repetición: el broker reentrega la orden de cobro
        Mensaje cobrar = new Mensaje("m-1", "cobrar", "viaje-1");
        recibir(cobrar);
        recibir(cobrar);

        // 2. Desorden: la cancelación de viaje-2 llega antes que su reserva
        recibir(new Mensaje("m-3", "cancelar-hotel", "viaje-2"));
        recibir(new Mensaje("m-2", "reservar-hotel", "viaje-2"));

        // 3. Compensación que falla: el servicio de hoteles está inestable
        hoteles.add("viaje-3");
        try {
            cancelarEnServicioInestable("viaje-3");
        } catch (IllegalStateException error) {
            System.out.println("no se pudo compensar viaje-3: " + error.getMessage() + " (se registra y se sigue)");
        }

        System.out.println();
        System.out.println("Veces que se cobró viaje-1: " + vecesCobrado.get("viaje-1"));
        System.out.println("Hoteles reservados:         " + hoteles);
    }

    static void recibir(Mensaje mensaje) {
        switch (mensaje.tipo()) {
            case "cobrar" -> vecesCobrado.merge(mensaje.saga(), 1, Integer::sum);
            case "reservar-hotel" -> hoteles.add(mensaje.saga());
            case "cancelar-hotel" -> hoteles.remove(mensaje.saga());    // si aún no existe, no hace nada
            default -> throw new IllegalArgumentException(mensaje.tipo());
        }
        System.out.println("procesado " + mensaje.id() + " " + mensaje.tipo() + " de " + mensaje.saga());
    }

    /** Falla las dos primeras veces que se llama. */
    static void cancelarEnServicioInestable(String saga) {
        llamadasAlServicioDeHoteles++;
        if (llamadasAlServicioDeHoteles <= 2) {
            throw new IllegalStateException("el servicio de hoteles no responde");
        }
        hoteles.remove(saga);
    }
}
