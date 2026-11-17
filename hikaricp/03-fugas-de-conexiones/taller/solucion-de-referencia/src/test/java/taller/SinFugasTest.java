package taller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static taller.Caminos.activasTrasTodosLosCaminos;
import static taller.Caminos.sinExcepciones;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Paso 11: cada extremo corregido, por todos sus caminos, termina con cero conexiones activas. Necesita la base del entorno. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SinFugasTest {

    @Autowired
    Clientes clientes;

    @Autowired
    DataSource dataSource;

    @Test
    void tryWithResourcesDevuelveLaConexionPorTodosLosCaminos() {
        assertEquals(0, activasTrasTodosLosCaminos(dataSource, sinExcepciones(clientes::corregido)));
    }

    @Test
    void jdbcTemplateDevuelveLaConexionPorTodosLosCaminos() {
        assertEquals(0, activasTrasTodosLosCaminos(dataSource, sinExcepciones(clientes::conPlantilla)));
    }

    @Test
    void laTransaccionAMediasNoFugaLaConexion() {
        assertEquals(0, activasTrasTodosLosCaminos(dataSource, sinExcepciones(clientes::transaccionAMedias)));
    }
}
