package tienda.adaptadores.salida.memoria;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import tienda.aplicacion.puertos.salida.Inventario;
import tienda.dominio.Existencias;

public final class InventarioEnMemoria implements Inventario {

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
