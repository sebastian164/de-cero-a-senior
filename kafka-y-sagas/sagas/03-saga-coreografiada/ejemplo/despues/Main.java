import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Coreografía: nadie llama a nadie. Cada servicio reacciona a eventos y publica los suyos. */
public class Main {

    record Evento(String tipo, String viaje, int valor) {}

    // El broker
    static final Map<String, List<Consumer<Evento>>> suscriptores = new LinkedHashMap<>();
    static final Deque<Evento> cola = new ArrayDeque<>();
    static final List<String> traza = new ArrayList<>();

    // La base de datos de cada servicio
    static final Set<String> vuelos = new LinkedHashSet<>();
    static final Set<String> hoteles = new LinkedHashSet<>();
    static final Set<String> cobros = new LinkedHashSet<>();
    static final Map<String, String> estadoDelViaje = new LinkedHashMap<>();
    static final List<String> correos = new ArrayList<>();

    public static void main(String[] args) {
        // Vuelos
        suscribir("ViajeSolicitado", e -> {
            vuelos.add(e.viaje());
            publicar("VueloReservado", e);
        });
        suscribir("HotelCancelado", e -> {
            vuelos.remove(e.viaje());
            publicar("VueloCancelado", e);
        });

        // Hoteles
        suscribir("VueloReservado", e -> {
            hoteles.add(e.viaje());
            publicar("HotelReservado", e);
        });
        suscribir("PagoRechazado", e -> {
            hoteles.remove(e.viaje());
            publicar("HotelCancelado", e);
        });

        // Pagos
        suscribir("HotelReservado", e -> {
            if (e.valor() > 1000) {
                publicar("PagoRechazado", e);
            } else {
                cobros.add(e.viaje());
                publicar("PagoAprobado", e);
            }
        });

        // Reservas: quien inició el viaje registra cómo terminó
        suscribir("PagoAprobado", e -> estadoDelViaje.put(e.viaje(), "CONFIRMADO"));
        suscribir("VueloCancelado", e -> estadoDelViaje.put(e.viaje(), "CANCELADO"));

        // Notificaciones: un servicio nuevo, añadido sin tocar ninguno de los anteriores
        suscribir("PagoAprobado", e -> correos.add("confirmación de " + e.viaje()));
        suscribir("VueloCancelado", e -> correos.add("disculpas por " + e.viaje()));

        publicar("ViajeSolicitado", new Evento("", "viaje-1", 800));
        despachar();
        publicar("ViajeSolicitado", new Evento("", "viaje-2", 5000));
        despachar();

        System.out.println("Vuelos:  " + vuelos);
        System.out.println("Hoteles: " + hoteles);
        System.out.println("Cobros:  " + cobros);
        System.out.println("Estados: " + estadoDelViaje);
        System.out.println("Correos: " + correos);
        System.out.println();
        System.out.println("Lo único que cuenta la historia completa es la traza de eventos:");
        traza.forEach(linea -> System.out.println("  " + linea));
    }

    static void suscribir(String tipo, Consumer<Evento> reaccion) {
        suscriptores.computeIfAbsent(tipo, t -> new ArrayList<>()).add(reaccion);
    }

    static void publicar(String tipo, Evento origen) {
        cola.add(new Evento(tipo, origen.viaje(), origen.valor()));
    }

    static void despachar() {
        while (!cola.isEmpty()) {
            Evento evento = cola.poll();
            traza.add(evento.viaje() + ": " + evento.tipo());
            for (Consumer<Evento> reaccion : suscriptores.getOrDefault(evento.tipo(), List.of())) {
                reaccion.accept(evento);
            }
        }
    }
}
