package tienda.aplicacion.puertos.entrada;

import java.util.List;
import tienda.aplicacion.VistaDePedido;

public interface CrearPedido {

    /** La clave la elige quien envía: repetir la misma solicitud no crea otro pedido. */
    record Solicitud(String clave, String cliente, List<Linea> lineas) {

        public Solicitud {
            lineas = lineas == null ? List.of() : List.copyOf(lineas);
        }
    }

    record Linea(String producto, int cantidad) {
    }

    /** nuevo es falso cuando la clave ya se había atendido: el pedido es el de la primera vez. */
    record Resultado(VistaDePedido pedido, boolean nuevo) {
    }

    Resultado crear(Solicitud solicitud);
}
