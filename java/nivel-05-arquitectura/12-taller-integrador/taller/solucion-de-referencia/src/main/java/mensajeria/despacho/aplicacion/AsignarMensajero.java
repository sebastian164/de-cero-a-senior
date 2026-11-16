package mensajeria.despacho.aplicacion;

import mensajeria.despacho.aplicacion.puertos.Envios;
import mensajeria.despacho.aplicacion.puertos.Mensajeros;
import mensajeria.despacho.aplicacion.puertos.Notificaciones;
import mensajeria.despacho.dominio.Envio;
import mensajeria.despacho.dominio.Mensajero;
import mensajeria.despacho.dominio.ReglaDeDespacho;

import java.util.Comparator;
import java.util.Optional;

/** Elige el mensajero compatible más cercano. Si otro proceso lo modificó a la vez, reintenta con datos frescos. */
public class AsignarMensajero {
    static final double RADIO_KM = 5;
    static final int REINTENTOS = 3;

    private final Envios envios;
    private final Mensajeros mensajeros;
    private final Notificaciones notificaciones;

    public AsignarMensajero(Envios envios, Mensajeros mensajeros, Notificaciones notificaciones) {
        this.envios = envios; this.mensajeros = mensajeros; this.notificaciones = notificaciones;
    }

    public Optional<String> ejecutar(String envioId) {
        for (int intento = 1; intento <= REINTENTOS; intento++) {
            Envio envio = envios.buscar(envioId).orElseThrow(() -> new ReglaDeDespacho("Envío inexistente"));
            Optional<Mensajero> elegido = mensajeros.cercanosA(envio.origen(), RADIO_KM).stream()
                    .filter(m -> m.puedeTomar(envio))
                    .min(Comparator.comparingDouble(m -> m.ubicacion().distanciaA(envio.origen())));
            if (elegido.isEmpty()) return Optional.empty();
            Mensajero m = elegido.get();
            try {
                m.asignar(envio);
                mensajeros.guardar(m);            // falla si otro asignó a este mensajero entretanto
                envios.guardar(envio);
                notificaciones.ofrecerEnvio(m.id(), envio.id());
                return Optional.of(m.id());
            } catch (Mensajeros.ConflictoDeVersion conflicto) {
                // se recarga y se vuelve a decidir con el estado real
            }
        }
        return Optional.empty();
    }
}
