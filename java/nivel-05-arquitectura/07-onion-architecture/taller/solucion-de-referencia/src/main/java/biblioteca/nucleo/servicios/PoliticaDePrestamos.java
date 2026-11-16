package biblioteca.nucleo.servicios;

import biblioteca.nucleo.modelo.Ejemplar;
import biblioteca.nucleo.modelo.Prestamo;
import biblioteca.nucleo.modelo.PrestamoRechazado;
import biblioteca.nucleo.modelo.Socio;

import java.time.LocalDate;

/** Servicio de dominio: involucra socio, ejemplar y préstamos vigentes. Sin estado, sin repositorios. */
public class PoliticaDePrestamos {

    public Prestamo prestar(Socio socio, Ejemplar ejemplar, int prestamosActivos, LocalDate hoy) {
        if (socio.tieneMultas()) throw new PrestamoRechazado("El socio tiene multas pendientes");
        if (prestamosActivos >= socio.tipo().maximoPrestamos()) throw new PrestamoRechazado("Alcanzó el máximo de préstamos");
        ejemplar.marcarPrestado();
        return new Prestamo(socio.id(), ejemplar.codigo(), hoy, hoy.plusDays(socio.tipo().diasDePrestamo()));
    }
}
