package mensajeria.despacho.adaptadores;

import mensajeria.despacho.aplicacion.puertos.Envios;
import mensajeria.despacho.dominio.Envio;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class EnviosEnMemoria implements Envios {
    private final Map<String, Envio> datos = new ConcurrentHashMap<>();
    @Override public void guardar(Envio e) { datos.put(e.id(), e.copia()); }
    @Override public Optional<Envio> buscar(String id) { return Optional.ofNullable(datos.get(id)).map(Envio::copia); }
}
