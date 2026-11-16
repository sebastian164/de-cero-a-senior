package biblioteca.infraestructura;

import biblioteca.nucleo.modelo.Prestamo;
import biblioteca.nucleo.servicios.Repositorios;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PrestamosEnMemoria implements Repositorios.Prestamos {
    private final List<Prestamo> datos = new ArrayList<>();
    @Override public void guardar(Prestamo p) { if (!datos.contains(p)) datos.add(p); }
    @Override public int activosDe(String socioId) {
        return (int) datos.stream().filter(p -> p.activo() && p.socioId().equals(socioId)).count();
    }
    @Override public Optional<Prestamo> activoDelEjemplar(String codigo) {
        return datos.stream().filter(p -> p.activo() && p.ejemplar().equals(codigo)).findFirst();
    }
}
