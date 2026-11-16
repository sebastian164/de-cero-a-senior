package transacciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import soporte.Entorno;

/** Pasos 4 y 5: 50 hilos contra el mismo inventario, y fallos de serialización con reintento. */
@SpringBootTest(classes = AplicacionInventario.class)
class SpringConcurrenciaTest {

    @DynamicPropertySource
    static void base(DynamicPropertyRegistry r) {
        String url = Entorno.baseNueva("n8_03_spring");
        r.add("spring.datasource.url", () -> url);
        r.add("spring.datasource.username", () -> Entorno.USUARIO);
        r.add("spring.datasource.password", () -> Entorno.CLAVE);
        r.add("spring.datasource.hikari.maximum-pool-size", () -> "50");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired ServicioInventario servicio;
    @Autowired InventarioRepositorio repositorio;
    @Autowired Guardias guardias;
    @Autowired GuardiasConReintento guardiasConReintento;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        repositorio.deleteAll();
        repositorio.save(new Inventario(1L, 100));
        jdbc.execute("DROP TABLE IF EXISTS medicos");
        jdbc.execute("CREATE TABLE medicos (nombre TEXT PRIMARY KEY, de_guardia BOOLEAN NOT NULL)");
        jdbc.execute("INSERT INTO medicos SELECT 'medico' || g, true FROM generate_series(1, 5) g");
    }

    static <T> List<Future<T>> enParalelo(int hilos, Function<Integer, Callable<T>> trabajo) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<T>> f = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            Callable<T> t = trabajo.apply(i);
            f.add(pool.submit(() -> {
                salida.await();
                return t.call();
            }));
        }
        salida.countDown();
        pool.shutdown();
        return f;
    }

    @Test
    void cincuenta_hilos_con_leer_calcular_escribir_pierden_ventas() throws Exception {
        for (Future<Object> f : enParalelo(50, i -> () -> { servicio.venderIngenuo(1L); return null; })) {
            f.get();
        }
        int stock = repositorio.findById(1L).orElseThrow().getStock();
        System.out.println("Ingenuo: 50 ventas, stock final " + stock + " (debería ser 50)");
        assertThat(stock).isGreaterThan(50);
    }

    @Test
    void cincuenta_hilos_con_update_atomico_no_pierden_ninguna() throws Exception {
        for (Future<Boolean> f : enParalelo(50, i -> () -> servicio.venderAtomico(1L))) {
            assertThat(f.get()).isTrue();
        }
        assertThat(repositorio.findById(1L).orElseThrow().getStock()).isEqualTo(50);
    }

    record Resultado(int aprobadas, int rechazadas, int errores, int quedan) {}

    Resultado cincoPidenSalida(Function<String, Boolean> pedir) throws Exception {
        AtomicInteger aprobadas = new AtomicInteger();
        AtomicInteger rechazadas = new AtomicInteger();
        AtomicInteger errores = new AtomicInteger();
        for (Future<Object> f : enParalelo(5, i -> () -> {
            try {
                (pedir.apply("medico" + (i + 1)) ? aprobadas : rechazadas).incrementAndGet();
            } catch (ConcurrencyFailureException e) {
                errores.incrementAndGet();
            }
            return null;
        })) {
            f.get();
        }
        Integer quedan = jdbc.queryForObject("SELECT count(*) FROM medicos WHERE de_guardia", Integer.class);
        Resultado r = new Resultado(aprobadas.get(), rechazadas.get(), errores.get(), quedan);
        System.out.println(r);
        return r;
    }

    @Test
    void read_committed_rompe_la_regla_de_la_guardia() throws Exception {
        Resultado r = cincoPidenSalida(guardias::pedirSalidaReadCommitted);
        assertThat(r.quedan()).isZero();
    }

    @Test
    void serializable_sin_reintento_entrega_errores_al_usuario() throws Exception {
        Resultado r = cincoPidenSalida(guardias::pedirSalidaSerializable);
        assertThat(r.errores()).isPositive();
        assertThat(r.quedan()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void serializable_con_reintento_cumple_la_regla_sin_errores() throws Exception {
        Resultado r = cincoPidenSalida(guardiasConReintento::pedirSalida);
        assertThat(r).isEqualTo(new Resultado(4, 1, 0, 1));
    }
}
