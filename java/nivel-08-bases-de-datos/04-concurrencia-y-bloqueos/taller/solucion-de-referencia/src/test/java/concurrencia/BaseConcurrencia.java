package concurrencia;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import soporte.Entorno;

@SpringBootTest(classes = AplicacionProductos.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class BaseConcurrencia {

    static final String URL = Entorno.baseNueva("n8_04");

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> URL);
        r.add("spring.datasource.username", () -> Entorno.USUARIO);
        r.add("spring.datasource.password", () -> Entorno.CLAVE);
        r.add("spring.datasource.hikari.maximum-pool-size", () -> "60");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired ProductoRepositorio repositorio;

    @BeforeEach
    void producto() {
        repositorio.deleteAll();
        repositorio.save(new Producto(1L, "balón", new BigDecimal("100.00"), 100));
    }
}
