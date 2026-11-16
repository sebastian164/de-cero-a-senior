package tienda.aplicacion.puertos.salida;

import java.util.Optional;

/** Qué pedido creó cada clave de idempotencia. Se anota en la misma unidad de trabajo que el pedido. */
public interface PeticionesProcesadas {

    Optional<String> pedidoDe(String clave);

    void registrar(String clave, String pedidoId);
}
