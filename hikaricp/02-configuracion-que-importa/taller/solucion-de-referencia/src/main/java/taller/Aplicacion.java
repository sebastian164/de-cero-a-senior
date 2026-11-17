package taller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class Aplicacion {

    private final JdbcTemplate jdbc;

    public Aplicacion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static void main(String[] args) {
        SpringApplication.run(Aplicacion.class, args);
    }

    /** La consulta que pide el taller: unos 20 ms en la base, durmiendo. No gasta CPU de la base. */
    @GetMapping("/consulta")
    public String consulta() {
        jdbc.queryForObject("select pg_sleep(0.02)", String.class);
        return "ok";
    }

    /** Unos 20 ms en la base, pero de CPU: contar trescientas mil filas generadas. */
    @GetMapping("/cpu")
    public Long cpu() {
        return jdbc.queryForObject("select count(*) from generate_series(1, 300000)", Long.class);
    }
}
