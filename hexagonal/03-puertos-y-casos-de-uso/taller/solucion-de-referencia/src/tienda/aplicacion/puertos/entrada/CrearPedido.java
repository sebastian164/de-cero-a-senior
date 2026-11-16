package tienda.aplicacion.puertos.entrada;

import java.util.List;
import tienda.aplicacion.VistaDePedido;

public interface CrearPedido {

    record Solicitud(String cliente, List<Linea> lineas) {

        public Solicitud {
            lineas = lineas == null ? List.of() : List.copyOf(lineas);
        }
    }

    record Linea(String producto, int cantidad) {
    }

    VistaDePedido crear(Solicitud solicitud);
}
