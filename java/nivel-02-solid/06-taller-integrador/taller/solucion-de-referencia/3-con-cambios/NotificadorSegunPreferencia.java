import java.util.EnumMap;
import java.util.Map;

/** Elige el canal según la preferencia del dueño. AgendaCitas no se enteró del cambio. */
public final class NotificadorSegunPreferencia implements Notificador {
    private final Map<CanalAviso, Notificador> canales;

    public NotificadorSegunPreferencia(Map<CanalAviso, Notificador> canales) {
        this.canales = new EnumMap<>(canales);
    }

    @Override
    public void notificar(Dueno dueno, String mensaje) {
        Notificador canal = canales.get(dueno.canal());
        if (canal == null) throw new IllegalStateException("No hay notificador para " + dueno.canal());
        canal.notificar(dueno, mensaje);
    }
}
