package nosql.aplicacion;

import java.time.Instant;
import java.util.List;

/** Puerto de salida: los pedidos pendientes más viejos que un límite (la consulta de la tarea de cancelación). */
public interface PedidosAbandonados {

    List<String> idsPendientesAnterioresA(Instant limite, int max);
}
