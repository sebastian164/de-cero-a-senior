package banco.transferencias;

import banco.transferencias.adaptador.entrada.cli.TransferenciaCli;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REST y CLI contra el adaptador JPA (H2). */
@SpringBootTest
@AutoConfigureMockMvc
class AdaptadoresRealesTest {
    @Autowired MockMvc mvc;
    @Autowired TransferenciaCli cli;
    @Autowired JdbcTemplate jdbc;

    @Test
    void restYCliCompartenTodoSalvoLaTraduccion() throws Exception {
        mvc.perform(post("/transferencias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"1001\",\"destino\":\"2002\",\"centavos\":30000000}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.aceptada").value(true));
        mvc.perform(post("/transferencias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"1001\",\"destino\":\"2002\",\"centavos\":30000000}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.motivo").value("Supera el tope diario"));
        mvc.perform(post("/transferencias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"1001\",\"destino\":\"2002\",\"centavos\":-5}"))
                .andExpect(status().isBadRequest());

        var salida = new ByteArrayOutputStream();
        assertThat(cli.ejecutar(new String[]{"1001", "2002", "20000000"}, new PrintStream(salida))).isZero();

        // formato inválido: 400 por REST y código 2 por CLI, sin 500 ni traza, y sin registrar nada
        mvc.perform(post("/transferencias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"abc\",\"destino\":\"2002\",\"centavos\":100}"))
                .andExpect(status().isBadRequest());
        assertThat(cli.ejecutar(new String[]{"abc", "2002", "100"}, new PrintStream(salida))).isEqualTo(2);
        assertThat(cli.ejecutar(new String[]{"1001", "2002", "mucho"}, new PrintStream(salida))).isEqualTo(2);

        assertThat(jdbc.queryForObject("select saldo from cuentas where numero = '2002'", Long.class)).isEqualTo(50_000_000L);
        assertThat(jdbc.queryForObject("select count(*) from transferencias", Long.class)).isEqualTo(3L);
    }
}
