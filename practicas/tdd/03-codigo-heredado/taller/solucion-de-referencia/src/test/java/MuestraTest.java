import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Compara el código actual con la muestra de referencia, combinación por combinación. */
class MuestraTest {

    @Test
    void elCodigoDaLoMismoQueLaMuestraEnTodasLasCombinaciones() throws IOException {
        List<String> muestra;
        try (InputStream entrada = MuestraTest.class.getResourceAsStream("/muestra.txt")) {
            muestra = new String(entrada.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
        assertTrue(muestra.size() > 1000, "la muestra tiene " + muestra.size() + " líneas");

        List<String> diferencias = new ArrayList<>();
        for (String linea : muestra) {
            String[] campo = linea.split(";");
            double esperado = Double.parseDouble(campo[4]);
            double obtenido = Parqueadero.cobro(campo[0], Long.parseLong(campo[1]), LocalDateTime.parse(campo[2]),
                    Boolean.parseBoolean(campo[3]));
            if (esperado != obtenido) {
                diferencias.add(linea + " -> ahora " + obtenido);
            }
        }
        assertEquals(List.of(), diferencias);
    }
}
