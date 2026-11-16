package taller.singleton;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SingletonTest {

    @Test
    void perezosoSinSincronizarCreaVariasInstancias() throws Exception {
        int n = Carrera.contarConstrucciones(ConfiguracionPerezosa::getInstancia, ConfiguracionPerezosa.CONSTRUCCIONES);
        assertTrue(n > 1, "se esperaban varias construcciones y hubo " + n);
    }

    @Test
    void lasTresCorreccionesCreanUnaSola() throws Exception {
        assertEquals(1, Carrera.contarConstrucciones(ConfiguracionSincronizada::getInstancia, ConfiguracionSincronizada.CONSTRUCCIONES));
        assertEquals(1, Carrera.contarConstrucciones(ConfiguracionHolder::getInstancia, ConfiguracionHolder.CONSTRUCCIONES));
        assertEquals(1, Carrera.contarConstrucciones(() -> ConfiguracionEnum.INSTANCIA, ConfiguracionEnum.CONSTRUCCIONES));
    }

    @Test
    @Order(1)
    void conEstadoGlobalElResultadoDependeDelOrden() {
        RegistroGlobal.INSTANCIA.limpiar();
        assertEquals("B FALLA (alertas registradas: 2)", PruebasContaminadas.correr(true));
        RegistroGlobal.INSTANCIA.limpiar();
        assertEquals("B pasa", PruebasContaminadas.correr(false));
    }

    @Test
    void inyectadoCadaPruebaTieneSuInstancia() {
        assertEquals("alertas registradas: 1", new ServicioAlertas(new Registro()).alertar("A"));
        assertEquals("alertas registradas: 1", new ServicioAlertas(new Registro()).alertar("B"));
        Registro compartido = new Registro();
        new ServicioAlertas(compartido).alertar("x");
        assertEquals("alertas registradas: 2", new ServicioAlertas(compartido).alertar("y"),
                "una sola instancia compartida, si quien ensambla lo decide");
    }
}
