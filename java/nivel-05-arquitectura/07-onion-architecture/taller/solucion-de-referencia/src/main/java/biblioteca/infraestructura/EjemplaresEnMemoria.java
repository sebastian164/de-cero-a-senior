package biblioteca.infraestructura;

import biblioteca.nucleo.modelo.Ejemplar;
import biblioteca.nucleo.servicios.Repositorios;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EjemplaresEnMemoria implements Repositorios.Ejemplares {
    private final Map<String, Ejemplar> datos = new HashMap<>();
    @Override public Optional<Ejemplar> buscar(String codigo) { return Optional.ofNullable(datos.get(codigo)); }
    @Override public void guardar(Ejemplar e) { datos.put(e.codigo(), e); }
}
