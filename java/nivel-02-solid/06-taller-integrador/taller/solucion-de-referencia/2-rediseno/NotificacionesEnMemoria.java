import java.util.ArrayList;
import java.util.List;

/** Doble que anota los avisos en lugar de enviarlos. */
public final class NotificacionesEnMemoria implements Notificador {
    private final List<String> enviados = new ArrayList<>();
    @Override public void notificar(Dueno dueno, String mensaje) { enviados.add(dueno.correo() + ": " + mensaje); }
    public List<String> enviados() { return List.copyOf(enviados); }
}
