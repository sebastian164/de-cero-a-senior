package biblioteca.infraestructura;

import biblioteca.nucleo.modelo.Socio;
import biblioteca.nucleo.servicios.Repositorios;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SociosEnMemoria implements Repositorios.Socios {
    private final Map<String, Socio> datos = new HashMap<>();
    @Override public Optional<Socio> buscar(String id) { return Optional.ofNullable(datos.get(id)); }
    @Override public void guardar(Socio s) { datos.put(s.id(), s); }
}
