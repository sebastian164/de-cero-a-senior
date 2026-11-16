package biblioteca.aplicacion;

import biblioteca.nucleo.modelo.Ejemplar;
import biblioteca.nucleo.modelo.Prestamo;
import biblioteca.nucleo.modelo.PrestamoRechazado;
import biblioteca.nucleo.modelo.Socio;
import biblioteca.nucleo.servicios.PoliticaDePrestamos;
import biblioteca.nucleo.servicios.Repositorios;

import java.time.Clock;
import java.time.LocalDate;

/** Servicio de aplicación: orquesta, no decide. */
public class PrestarEjemplar {
    private final Repositorios.Socios socios;
    private final Repositorios.Ejemplares ejemplares;
    private final Repositorios.Prestamos prestamos;
    private final PoliticaDePrestamos politica = new PoliticaDePrestamos();
    private final Clock reloj;

    public PrestarEjemplar(Repositorios.Socios s, Repositorios.Ejemplares e, Repositorios.Prestamos p, Clock reloj) {
        this.socios = s; this.ejemplares = e; this.prestamos = p; this.reloj = reloj;
    }

    public Prestamo ejecutar(String socioId, String codigoEjemplar) {
        Socio socio = socios.buscar(socioId).orElseThrow(() -> new PrestamoRechazado("Socio inexistente"));
        Ejemplar ejemplar = ejemplares.buscar(codigoEjemplar).orElseThrow(() -> new PrestamoRechazado("Ejemplar inexistente"));
        Prestamo prestamo = politica.prestar(socio, ejemplar, prestamos.activosDe(socioId), LocalDate.now(reloj));
        ejemplares.guardar(ejemplar);
        prestamos.guardar(prestamo);
        return prestamo;
    }
}
