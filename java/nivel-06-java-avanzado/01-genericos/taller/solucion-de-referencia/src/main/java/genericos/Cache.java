package genericos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Caché con capacidad máxima: al llenarse expulsa la entrada más antigua (por orden de inserción). */
public class Cache<K, V> {
    private final int capacidad;
    private final LinkedHashMap<K, V> datos;

    public Cache(int capacidad) {
        if (capacidad <= 0) throw new IllegalArgumentException("capacidad debe ser positiva");
        this.capacidad = capacidad;
        this.datos = new LinkedHashMap<>(16, 0.75f, false) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> masAntigua) {
                return size() > Cache.this.capacidad;
            }
        };
    }

    public void poner(K clave, V valor) { datos.put(clave, valor); }
    public Optional<V> obtener(K clave) { return Optional.ofNullable(datos.get(clave)); }
    public int tamano() { return datos.size(); }
}
