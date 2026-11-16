package biblioteca.aplicacion;

import biblioteca.nucleo.modelo.Ejemplar;
import biblioteca.nucleo.modelo.Prestamo;
import biblioteca.nucleo.modelo.Socio;
import biblioteca.nucleo.servicios.Repositorios;

import java.time.Clock;
import java.time.LocalDate;

public class DevolverEjemplar {
    private final Repositorios.Socios socios;
    private final Repositorios.Ejemplares ejemplares;
    private final Repositorios.Prestamos prestamos;
    private final Clock reloj;

    public DevolverEjemplar(Repositorios.Socios s, Repositorios.Ejemplares e, Repositorios.Prestamos p, Clock reloj) {
        this.socios = s; this.ejemplares = e; this.prestamos = p; this.reloj = reloj;
    }

    public long ejecutar(String codigoEjemplar) {
        Prestamo prestamo = prestamos.activoDelEjemplar(codigoEjemplar).orElseThrow();
        long multa = prestamo.devolver(LocalDate.now(reloj));
        Ejemplar ejemplar = ejemplares.buscar(codigoEjemplar).orElseThrow();
        ejemplar.marcarDevuelto();
        Socio socio = socios.buscar(prestamo.socioId()).orElseThrow();
        socio.cargarMulta(multa);
        prestamos.guardar(prestamo);
        ejemplares.guardar(ejemplar);
        socios.guardar(socio);
        return multa;
    }
}
