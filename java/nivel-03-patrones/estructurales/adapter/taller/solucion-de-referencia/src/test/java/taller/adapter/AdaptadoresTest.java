package taller.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AdaptadoresTest {

    static Stream<ServicioClima> servicios() {
        return Stream.of(new AdaptadorClimaTexto(new ProveedorClimaTexto()), new AdaptadorClimaMapa(new ProveedorClimaMapa()));
    }

    @ParameterizedTest
    @MethodSource("servicios")
    void ambosTraducenUnidadesAlMismoPronostico(ServicioClima clima) {
        assertEquals(new Pronostico(12.0, 0.40), clima.pronosticoPara("Tunja"));
        assertEquals(new Pronostico(18.0, 0.75), clima.pronosticoPara("Paipa"));
    }

    @ParameterizedTest
    @MethodSource("servicios")
    void ciudadInexistenteSeComportaIgual(ServicioClima clima) {
        var e = assertThrows(CiudadNoEncontradaException.class, () -> clima.pronosticoPara("Atlantida"));
        assertEquals("Ciudad no encontrada: Atlantida", e.getMessage());
    }

    @ParameterizedTest
    @MethodSource("servicios")
    void elRecomendadorFuncionaConAmbosSinCambiar(ServicioClima clima) {
        Recomendador r = new Recomendador(clima);
        assertEquals("no hace falta sombrilla", r.recomendar("Tunja"));
        assertEquals("lleva sombrilla", r.recomendar("Paipa"));
        assertEquals("sin datos para Atlantida", r.recomendar("Atlantida"));
    }

    @Test
    void reintentaFallosTransitoriosPeroNoCiudadInexistente() {
        ProveedorClimaMapa p = new ProveedorClimaMapa();
        p.fallarProximas(2);
        assertEquals(new Pronostico(12.0, 0.40), new ConReintentos(new AdaptadorClimaMapa(p), 3).pronosticoPara("Tunja"));
        assertEquals(3, p.llamadas());

        ProveedorClimaMapa q = new ProveedorClimaMapa();
        q.fallarProximas(5);
        assertThrows(ClimaNoDisponibleException.class, () -> new ConReintentos(new AdaptadorClimaMapa(q), 3).pronosticoPara("Tunja"));
        assertEquals(3, q.llamadas());

        ProveedorClimaMapa r = new ProveedorClimaMapa();
        assertThrows(CiudadNoEncontradaException.class, () -> new ConReintentos(new AdaptadorClimaMapa(r), 3).pronosticoPara("X"));
        assertEquals(1, r.llamadas(), "una ciudad inexistente no se reintenta");
    }
}
