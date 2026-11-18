package acortador;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Sin CLAVE_CODIGOS (ni propiedad equivalente) la aplicación no arranca: no hay una clave pública por defecto. */
class AcortadorSinClaveTest {
    @Test
    void sinClaveNoArranca() {
        assumeSinVariable();
        Throwable error = assertThrows(Throwable.class, () ->
            new SpringApplicationBuilder(AplicacionAcortador.class).web(WebApplicationType.NONE).properties("spring.main.banner-mode=off").run().close());
        Throwable causa = error;
        while (causa.getCause() != null) causa = causa.getCause();
        assertTrue(String.valueOf(error.getMessage() + causa.getMessage()).contains("CLAVE_CODIGOS"), "debe nombrar la variable que falta: " + causa);
    }

    @Test
    void conElPerfilDeDesarrolloSiArranca() {
        assumeSinVariable();
        try (var ctx = new SpringApplicationBuilder(AplicacionAcortador.class).web(WebApplicationType.NONE).profiles("desarrollo").run()) {
            String clave = ctx.getBean(Propiedades.class).claveSecreta();
            assertEquals("solo-para-desarrollo-no-usar-en-produccion", clave);
            assertFalse(clave.startsWith("${"), "la clave efectiva nunca es un marcador sin resolver");
        }
    }

    private static void assumeSinVariable() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("CLAVE_CODIGOS") == null, "la variable CLAVE_CODIGOS está definida en este entorno");
    }
}
