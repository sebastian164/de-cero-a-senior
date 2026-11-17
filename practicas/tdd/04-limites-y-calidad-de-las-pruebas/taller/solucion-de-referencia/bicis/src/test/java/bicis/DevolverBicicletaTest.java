package bicis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bicis.CobrosEspia.Cobro;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** El caso de uso completo, con dobles hechos a mano. */
class DevolverBicicletaTest {

    private final AlquileresEnMemoria alquileres = new AlquileresEnMemoria();
    private final BicicletasEnMemoria bicicletas = new BicicletasEnMemoria();
    private final CobrosEspia cobros = new CobrosEspia();
    private final RelojFijo reloj = new RelojFijo();
    private final DevolverBicicleta devolver = new DevolverBicicleta(alquileres, bicicletas, cobros, reloj);

    private static LocalDateTime hoyALas(int hora, int minuto) {
        return LocalDateTime.of(2026, 11, 15, hora, minuto);
    }

    // Olor corregido (paso 7): la prueba original tenía dos razones para fallar, el cobro y la bicicleta.
    @Test
    void dadoUnAlquilerDeLasDiezDevueltoALasDiezYCincuentaSeCobranDosBloques() {
        alquileres.guardar(Alquiler.iniciado("a-1", "ana", "bici-7", "centro", hoyALas(10, 0)));
        reloj.fijar(hoyALas(10, 50));

        devolver.ejecutar("a-1", "centro");

        assertEquals(List.of(new Cobro("ana", 2000)), cobros.registrados);
    }

    @Test
    void alDevolverlaEnLaMismaEstacionLaBicicletaQuedaDisponibleAlli() {
        alquileres.guardar(Alquiler.iniciado("a-1", "ana", "bici-7", "centro", hoyALas(10, 0)));
        reloj.fijar(hoyALas(10, 50));

        devolver.ejecutar("a-1", "centro");

        assertTrue(bicicletas.estaDisponibleEn("bici-7", "centro"));
    }

    @Test
    void devolverDentroDeLaMediaHoraGratisNoRegistraNingunPago() {
        alquileres.guardar(Alquiler.iniciado("a-1", "ana", "bici-7", "centro", hoyALas(10, 0)));
        reloj.fijar(hoyALas(10, 30));

        devolver.ejecutar("a-1", "centro");

        assertEquals(List.of(), cobros.registrados);
        assertTrue(bicicletas.estaDisponibleEn("bici-7", "centro"));
    }

    @Test
    void unAbonadoQueDevuelveALosCincuentaMinutosNoPaga() {
        alquileres.guardar(Alquiler.iniciadoPorAbonado("a-2", "luis", "bici-3", "centro", hoyALas(10, 0)));
        reloj.fijar(hoyALas(10, 50));

        devolver.ejecutar("a-2", "centro");

        assertEquals(List.of(), cobros.registrados);
    }

    @Test
    void devueltaEnOtraEstacionSeCobraElRecargoYQuedaDisponibleEnLaDeDestino() {
        alquileres.guardar(Alquiler.iniciado("a-1", "ana", "bici-7", "centro", hoyALas(10, 0)));
        reloj.fijar(hoyALas(10, 50));

        devolver.ejecutar("a-1", "norte");

        // Olor corregido (paso 7): el valor esperado copiaba la fórmula (2 * 1000 + 2000). Ahora es el número.
        assertEquals(List.of(new Cobro("ana", 4000)), cobros.registrados);
        assertTrue(bicicletas.estaDisponibleEn("bici-7", "norte"));
    }

    @Test
    void devolverUnAlquilerQueNoExisteEsUnError() {
        assertThrows(AlquilerNoDisponible.class, () -> devolver.ejecutar("no-existe", "centro"));
        assertEquals(List.of(), cobros.registrados);
    }

    @Test
    void unAlquilerYaCerradoNoSeCobraDosVeces() {
        alquileres.guardar(Alquiler.iniciado("a-1", "ana", "bici-7", "centro", hoyALas(10, 0)).cerrar());
        reloj.fijar(hoyALas(10, 50));

        assertThrows(AlquilerNoDisponible.class, () -> devolver.ejecutar("a-1", "centro"));
        assertEquals(List.of(), cobros.registrados);
    }
}
