package oficina.reservas.configuracion;

import oficina.reservas.aplicacion.CancelarReserva;
import oficina.reservas.aplicacion.ConsultarDisponibilidad;
import oficina.reservas.aplicacion.ReservarSala;
import oficina.reservas.aplicacion.puertos.RepositorioReservas;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Ensambla los casos de uso (sin anotaciones) como beans. */
@Configuration
class ReservasConfig {
    @Bean Clock reloj() { return Clock.systemDefaultZone(); }
    @Bean ReservarSala reservarSala(RepositorioReservas r) { return new ReservarSala(r); }
    @Bean CancelarReserva cancelarReserva(RepositorioReservas r, Clock reloj) { return new CancelarReserva(r, reloj); }
    @Bean ConsultarDisponibilidad consultarDisponibilidad(RepositorioReservas r) { return new ConsultarDisponibilidad(r); }
}
