package tienda;

import java.util.List;
import tienda.bus.Evento;

/** Paso 8: el estado de una compra a partir de la traza de eventos, sin preguntarle a ningún servicio. */
public final class Reconstruccion {

    private Reconstruccion() {
    }

    public static String estado(List<Evento> traza, String correlacion) {
        String estado = "DESCONOCIDA";
        boolean cobrado = false;
        boolean reservado = false;
        for (Evento e : traza) {
            if (!e.correlacion().equals(correlacion)) {
                continue;
            }
            switch (e.tipo()) {
                case "PedidoCreado" -> estado = "INICIADA";
                case "InventarioReservado" -> reservado = true;
                case "InventarioLiberado" -> reservado = false;
                case "PagoAprobado" -> cobrado = true;
                case "PagoReembolsado" -> cobrado = false;
                case "PedidoConfirmado" -> estado = "CONFIRMADA";
                case "PedidoCancelado" -> estado = "CANCELADA";
                default -> { }
            }
        }
        if (estado.equals("INICIADA")) {
            estado = "EN CURSO (" + (reservado ? "reservado" : "sin reserva") + ", " + (cobrado ? "cobrado" : "sin cobro") + ")";
        }
        return estado;
    }
}
