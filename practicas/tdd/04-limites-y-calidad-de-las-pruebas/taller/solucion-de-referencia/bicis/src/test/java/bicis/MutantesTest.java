package bicis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Pruebas que matan a los mutantes que sobrevivieron a las pruebas de la sesión 02. */
class MutantesTest {

    private final AlquileresEnMemoria alquileres = new AlquileresEnMemoria();
    private final BicicletasEnMemoria bicicletas = new BicicletasEnMemoria();
    private final CobrosEspia cobros = new CobrosEspia();
    private final Reloj alasDiezYCincuenta = () -> LocalDateTime.of(2026, 11, 15, 10, 50);
    private final DevolverBicicleta devolver = new DevolverBicicleta(alquileres, bicicletas, cobros, alasDiezYCincuenta);

    // Tarifa.diasIniciados: "+ dia - 1" mutado a "+ dia + 1". Con él, 24 horas exactas contarían como dos días.
    @Test
    void veinticuatroHorasExactasSonUnSoloDiaDeTope() {
        assertEquals(12_000, Tarifa.precio(Duration.ofHours(24)));
    }

    // DevolverBicicleta: se quitaba la llamada a alquileres.guardar(...). El alquiler quedaba abierto.
    @Test
    void despuesDeDevolverElAlquilerQuedaCerrado() {
        alquileres.guardar(Alquiler.iniciado("a-1", "ana", "bici-7", "centro", LocalDateTime.of(2026, 11, 15, 10, 0)));

        devolver.ejecutar("a-1", "centro");

        assertTrue(alquileres.buscar("a-1").orElseThrow().cerrado());
        assertThrows(AlquilerNoDisponible.class, () -> devolver.ejecutar("a-1", "centro"));
    }

    // DevolverBicicleta: "return valor" mutado a "return 0". Nadie miraba lo que devuelve el caso de uso.
    @Test
    void elCasoDeUsoDevuelveLoQueCobro() {
        alquileres.guardar(Alquiler.iniciado("a-1", "ana", "bici-7", "centro", LocalDateTime.of(2026, 11, 15, 10, 0)));

        assertEquals(4000, devolver.ejecutar("a-1", "norte"));
    }
}
