package bicis;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Las mismas comprobaciones de cobro que con CobrosEspia, con un simulado de Mockito. */
class DevolverBicicletaConMockitoTest {

    private final AlquileresEnMemoria alquileres = new AlquileresEnMemoria();
    private final BicicletasEnMemoria bicicletas = new BicicletasEnMemoria();
    private final Cobros cobros = mock(Cobros.class);
    private final Reloj alasDiezYCincuenta = () -> LocalDateTime.of(2026, 11, 15, 10, 50);
    private final DevolverBicicleta devolver = new DevolverBicicleta(alquileres, bicicletas, cobros, alasDiezYCincuenta);

    @Test
    void devueltaEnOtraEstacionRegistraLosBloquesMasElRecargo() {
        alquileres.guardar(Alquiler.iniciado("a-1", "ana", "bici-7", "centro", LocalDateTime.of(2026, 11, 15, 10, 0)));

        devolver.ejecutar("a-1", "norte");

        verify(cobros).registrar("ana", 4000);
        verifyNoMoreInteractions(cobros);
    }

    @Test
    void unAbonadoDentroDeSuHoraNoGeneraNingunCobro() {
        alquileres.guardar(Alquiler.iniciadoPorAbonado("a-2", "luis", "bici-3", "centro", LocalDateTime.of(2026, 11, 15, 10, 0)));

        devolver.ejecutar("a-2", "centro");

        verifyNoInteractions(cobros);
    }
}
