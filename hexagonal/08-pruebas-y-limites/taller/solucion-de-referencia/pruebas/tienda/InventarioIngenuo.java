package tienda;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import tienda.aplicacion.puertos.salida.Inventario;
import tienda.dominio.Existencias;

/** Un inventario que guarda lo que le den, sin mirar versiones: la última escritura gana. */
public final class InventarioIngenuo implements Inventario {

    private final Map<String, Integer> unidades = new HashMap<>();

    @Override
    public synchronized Optional<Existencias> existenciasDe(String producto) {
        return Optional.ofNullable(unidades.get(producto)).map(cuantas -> Existencias.de(producto, cuantas));
    }

    @Override
    public synchronized void guardar(Existencias existencias) {
        unidades.put(existencias.producto(), existencias.unidades());
    }
}
