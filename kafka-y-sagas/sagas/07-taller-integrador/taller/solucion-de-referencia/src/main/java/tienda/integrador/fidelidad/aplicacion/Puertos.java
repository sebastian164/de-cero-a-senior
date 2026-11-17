package tienda.integrador.fidelidad.aplicacion;

/** Puertos de Fidelidad. Sin Kafka, SQL ni Spring. */
public final class Puertos {

    private Puertos() {
    }

    public interface ReaccionarAlResultado {
        record Resultado(String eventoId, String pedidoId, String correlacion, boolean completada, String motivo) {
        }

        void reaccionar(Resultado resultado);
    }

    public interface Puntos {
        /** true si es la primera vez que se registra este caso (el mismo evento dos veces no repite el efecto). */
        boolean sumar(String pedidoId, int puntos);
    }
}
