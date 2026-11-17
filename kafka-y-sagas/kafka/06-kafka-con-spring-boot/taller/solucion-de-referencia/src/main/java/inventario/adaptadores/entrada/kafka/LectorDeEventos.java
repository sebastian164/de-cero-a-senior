package inventario.adaptadores.entrada.kafka;

/** Colaborador del listener: lee el mensaje del tema: la forma de serializar es un detalle del adaptador. */
public interface LectorDeEventos {
    <T> T leer(String json, Class<T> tipo);
}
