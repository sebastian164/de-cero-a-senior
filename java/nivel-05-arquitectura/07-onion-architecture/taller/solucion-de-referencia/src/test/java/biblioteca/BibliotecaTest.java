package biblioteca;

import biblioteca.aplicacion.DevolverEjemplar;
import biblioteca.aplicacion.PrestarEjemplar;
import biblioteca.infraestructura.EjemplaresEnMemoria;
import biblioteca.infraestructura.PrestamosEnMemoria;
import biblioteca.infraestructura.SociosEnMemoria;
import biblioteca.nucleo.modelo.Ejemplar;
import biblioteca.nucleo.modelo.Prestamo;
import biblioteca.nucleo.modelo.PrestamoRechazado;
import biblioteca.nucleo.modelo.Socio;
import biblioteca.nucleo.modelo.TipoSocio;
import biblioteca.nucleo.servicios.PoliticaDePrestamos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BibliotecaTest {

    static final LocalDate HOY = LocalDate.of(2030, 2, 1);

    // ---- núcleo, sin repositorios ----
    @Test
    void politicaRechazaSocioConMultas() {
        assertThatThrownBy(() -> new PoliticaDePrestamos().prestar(new Socio("s", TipoSocio.ESTUDIANTE, 500),
                new Ejemplar("e", false, false), 0, HOY)).hasMessageContaining("multas");
    }

    @Test
    void politicaRespetaElMaximoPorTipoYElPlazo() {
        var politica = new PoliticaDePrestamos();
        assertThatThrownBy(() -> politica.prestar(new Socio("s", TipoSocio.ESTUDIANTE, 0), new Ejemplar("e", false, false), 3, HOY))
                .hasMessageContaining("máximo");
        Prestamo docente = politica.prestar(new Socio("d", TipoSocio.DOCENTE, 0), new Ejemplar("e", false, false), 7, HOY);
        assertThat(docente.limite()).isEqualTo(HOY.plusDays(30));
    }

    @Test
    void elEjemplarProtegeSusReglas() {
        assertThatThrownBy(() -> new Ejemplar("r", true, false).marcarPrestado()).hasMessageContaining("referencia");
        Ejemplar e = new Ejemplar("e", false, false);
        e.marcarPrestado();
        assertThatThrownBy(e::marcarPrestado).hasMessageContaining("ya está prestado");
    }

    @Test
    void multaPorDiaDeRetraso() {
        Prestamo p = new Prestamo("s", "e", HOY, HOY.plusDays(15));
        assertThat(p.devolver(HOY.plusDays(18))).isEqualTo(3 * Prestamo.MULTA_POR_DIA);
        assertThat(new Prestamo("s", "e", HOY, HOY.plusDays(15)).devolver(HOY.plusDays(15))).isZero();
    }

    // ---- aplicación + anillo exterior ----
    SociosEnMemoria socios; EjemplaresEnMemoria ejemplares; PrestamosEnMemoria prestamos;

    @BeforeEach
    void preparar() {
        socios = new SociosEnMemoria(); ejemplares = new EjemplaresEnMemoria(); prestamos = new PrestamosEnMemoria();
        socios.guardar(new Socio("ana", TipoSocio.ESTUDIANTE, 0));
        ejemplares.guardar(new Ejemplar("E1", false, false));
    }

    static Clock en(LocalDate dia) { return Clock.fixed(dia.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC); }

    @Test
    void prestarDevolverTardeYQuedarBloqueado() {
        new PrestarEjemplar(socios, ejemplares, prestamos, en(HOY)).ejecutar("ana", "E1");
        assertThatThrownBy(() -> new PrestarEjemplar(socios, ejemplares, prestamos, en(HOY)).ejecutar("ana", "E1"))
                .isInstanceOf(PrestamoRechazado.class);
        long multa = new DevolverEjemplar(socios, ejemplares, prestamos, en(HOY.plusDays(20))).ejecutar("E1");
        assertThat(multa).isEqualTo(5 * Prestamo.MULTA_POR_DIA);
        assertThatThrownBy(() -> new PrestarEjemplar(socios, ejemplares, prestamos, en(HOY.plusDays(21))).ejecutar("ana", "E1"))
                .hasMessageContaining("multas");
    }
}
