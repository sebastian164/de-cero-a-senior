package tienda.integrador.notificaciones.aplicacion;

/** Puertos de Notificaciones. Sin Kafka, SQL ni Spring. */
public final class Puertos {

    private Puertos() {
    }

    public interface ReaccionarAlResultado {
        record Resultado(String eventoId, String pedidoId, String correlacion, boolean completada, String motivo) {
        }

        void reaccionar(Resultado resultado);
    }

    public interface RegistroDeNotificaciones {
        /** true si es la primera vez que se registra este caso (el mismo evento dos veces no repite el efecto). */
        boolean registrar(String eventoId, String pedidoId, String mensaje);
    }
}
