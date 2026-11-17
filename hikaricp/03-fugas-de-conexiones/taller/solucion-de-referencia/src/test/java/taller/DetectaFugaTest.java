package taller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static taller.Caminos.activasTrasTodosLosCaminos;
import static taller.Caminos.sinExcepciones;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/** La prueba del paso 11 contra el extremo con fuga: demuestra que fallaría si la fuga volviera. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext                                          // el pool queda con conexiones fugadas: no se reutiliza
class DetectaFugaTest {

    @Autowired
    Clientes clientes;

    @Autowired
    DataSource dataSource;

    @Test
    void laPruebaDetectaLaFuga() {
        int activas = activasTrasTodosLosCaminos(dataSource, sinExcepciones(clientes::conFuga));
        assertTrue(activas == 3, "tres vueltas por el camino del id negativo dejan tres conexiones fuera: " + activas);
    }
}
