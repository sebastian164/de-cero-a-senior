package concurrencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import soporte.Entorno;

/**
 * Paso 6. Dos contextos de Spring independientes contra la misma base equivalen a dos réplicas:
 * cada una tiene su propio bean y por tanto su propio monitor de synchronized.
 */
class SynchronizedDosInstanciasTest {

    static ConfigurableApplicationContext instancia(String url) {
        return new SpringApplicationBuilder(AplicacionProductos.class).web(WebApplicationType.NONE)
                .properties("spring.datasource.url=" + url, "spring.datasource.username=curso",
                        "spring.datasource.password=curso", "spring.jpa.hibernate.ddl-auto=update",
                        "spring.datasource.hikari.maximum-pool-size=30")
                .run();
    }

    @Test
    void synchronized_protege_una_instancia_pero_no_dos() throws Exception {
        String url = Entorno.baseNueva("n8_04_sync");
        try (ConfigurableApplicationContext a = instancia(url); ConfigurableApplicationContext b = instancia(url)) {
            ProductoRepositorio repo = a.getBean(ProductoRepositorio.class);
            InventarioSincronizado enA = a.getBean(InventarioSincronizado.class);
            InventarioSincronizado enB = b.getBean(InventarioSincronizado.class);

            repo.save(new Producto(1L, "balón", BigDecimal.TEN, 100));
            Paralelo.correr(40, i -> enA.vender(1L));
            int unaInstancia = repo.findById(1L).orElseThrow().getStock();

            repo.deleteAll();
            repo.save(new Producto(1L, "balón", BigDecimal.TEN, 100));
            Paralelo.correr(40, i -> (i % 2 == 0 ? enA : enB).vender(1L));
            int dosInstancias = repo.findById(1L).orElseThrow().getStock();

            System.out.println("synchronized: una instancia → stock " + unaInstancia + "; dos instancias → stock " + dosInstancias + " (esperado 60)");
            assertThat(unaInstancia).isEqualTo(60);
            assertThat(dosInstancias).isGreaterThan(60);
        }
    }
}
