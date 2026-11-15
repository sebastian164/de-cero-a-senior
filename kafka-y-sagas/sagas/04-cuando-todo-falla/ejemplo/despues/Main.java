import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Participantes idempotentes, con lápidas para el desorden y compensaciones que insisten. */
public class Main {

    record Mensaje(String id, String tipo, String saga) {}

    static final Map<String, Integer> vecesCobrado = new LinkedHashMap<>();
    static final Set<String> hoteles = new LinkedHashSet<>();
    static final Set<String> mensajesProcesados = new LinkedHashSet<>();     // misma transacción que el efecto
    static final Set<String> sagasCanceladas = new LinkedHashSet<>();        // lápidas
    static final List<String> casosParaRevision = new ArrayList<>();
    static int llamadasAlServicioDeHoteles = 0;

    public static void main(String[] args) {
        Mensaje cobrar = new Mensaje("m-1", "cobrar", "viaje-1");
        recibir(cobrar);
        recibir(cobrar);

        recibir(new Mensaje("m-3", "cancelar-hotel", "viaje-2"));
        recibir(new Mensaje("m-2", "reservar-hotel", "viaje-2"));

        hoteles.add("viaje-3");
        compensarInsistiendo("viaje-3", 5);

        System.out.println();
        System.out.println("Veces que se cobró viaje-1: " + vecesCobrado.get("viaje-1"));
        System.out.println("Hoteles reservados:         " + hoteles);
        System.out.println("Casos para revisión humana: " + casosParaRevision);
    }

    static void recibir(Mensaje mensaje) {
        if (!mensajesProcesados.add(mensaje.id())) {
            System.out.println("repetido " + mensaje.id() + ": se ignora");
            return;
        }
        switch (mensaje.tipo()) {
            case "cobrar" -> vecesCobrado.merge(mensaje.saga(), 1, Integer::sum);
            case "reservar-hotel" -> {
                if (sagasCanceladas.contains(mensaje.saga())) {
                    System.out.println(mensaje.id() + ": " + mensaje.saga() + " ya fue cancelada; no se reserva");
                    return;
                }
                hoteles.add(mensaje.saga());
            }
            case "cancelar-hotel" -> {
                sagasCanceladas.add(mensaje.saga());            // queda anotado aunque no haya nada que cancelar
                hoteles.remove(mensaje.saga());
            }
            default -> throw new IllegalArgumentException(mensaje.tipo());
        }
        System.out.println("procesado " + mensaje.id() + " " + mensaje.tipo() + " de " + mensaje.saga());
    }

    static void compensarInsistiendo(String saga, int intentosMaximos) {
        for (int intento = 1; intento <= intentosMaximos; intento++) {
            try {
                cancelarEnServicioInestable(saga);              // idempotente: repetirla no hace daño
                System.out.println("compensada " + saga + " en el intento " + intento);
                return;
            } catch (IllegalStateException error) {
                System.out.println("intento " + intento + " de compensar " + saga + " falló: " + error.getMessage());
                // en un sistema real: esperar cada vez más antes del siguiente intento
            }
        }
        casosParaRevision.add(saga);                            // con alerta: alguien tiene que mirarlo
        System.out.println(saga + " no se pudo compensar: pasa a revisión humana");
    }

    static void cancelarEnServicioInestable(String saga) {
        llamadasAlServicioDeHoteles++;
        if (llamadasAlServicioDeHoteles <= 2) {
            throw new IllegalStateException("el servicio de hoteles no responde");
        }
        hoteles.remove(saga);
    }
}
