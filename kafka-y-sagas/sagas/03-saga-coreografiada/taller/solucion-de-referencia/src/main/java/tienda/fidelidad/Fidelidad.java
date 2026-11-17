package tienda.fidelidad;

import java.util.LinkedHashSet;
import java.util.Set;
import tienda.bus.Bus;

/** Paso 4: un servicio nuevo que solo escucha. Ningún servicio existente cambió para añadirlo. */
public final class Fidelidad {

    public final Set<String> puntos = new LinkedHashSet<>();

    public Fidelidad(Bus bus) {
        bus.suscribir("PedidoConfirmado", e -> puntos.add(e.correlacion()));
    }
}
