package cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import soporte.Entorno;

@SpringBootTest(classes = AplicacionCatalogo.class)
class CacheTest {

    static {
        try (Connection c = Entorno.conectar(Entorno.baseNueva("n8_06"))) {
            c.createStatement().execute("""
                CREATE TABLE productos (id BIGINT PRIMARY KEY, nombre TEXT NOT NULL, precio NUMERIC(12,2) NOT NULL);
                INSERT INTO productos SELECT g, 'Producto ' || g, 1000 + g FROM generate_series(1, 100) g;
                """);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static final StringBuilder informe = new StringBuilder();

    @Autowired CatalogoProductos catalogo;
    @Autowired RepositorioProductos repositorio;
    @Autowired CacheManager caches;
    @Autowired RedisConnectionFactory redis;
    @Autowired StringRedisTemplate plantilla;

    @BeforeEach
    void limpiar() {
        redis.getConnection().serverCommands().flushDb();
        repositorio.reiniciarContador();
    }

    @AfterAll
    static void imprimir() {
        System.out.println(informe);
    }

    static void anotar(String formato, Object... args) {
        informe.append(String.format(Locale.ROOT, formato, args)).append('\n');
    }

    @Test
    void paso1_tiempos_y_tasa_de_aciertos() {
        Random azar = new Random(42);
        List<Long> pedidos = new ArrayList<>();
        for (int i = 0; i < 500; i++) {   // el 80 % de las consultas va a 10 productos, el resto a 100
            pedidos.add(azar.nextDouble() < 0.8 ? 1L + azar.nextInt(10) : 1L + azar.nextInt(100));
        }
        long t0 = System.nanoTime();
        pedidos.forEach(repositorio::buscarDto);
        double sinCache = (System.nanoTime() - t0) / 1e6;

        RedisCache cache = (RedisCache) caches.getCache("productos");
        cache.clearStatistics();
        repositorio.reiniciarContador();
        t0 = System.nanoTime();
        pedidos.forEach(catalogo::buscar);
        double conCache = (System.nanoTime() - t0) / 1e6;
        long aciertos = cache.getStatistics().getHits();
        long fallos = cache.getStatistics().getMisses();
        double tasa = (double) aciertos / (aciertos + fallos);
        anotar("Paso 1: 500 consultas. Sin caché %.0f ms (%d a la base). Con caché %.0f ms, %d a la base, aciertos %d, fallos %d, tasa %.1f %%",
                sinCache, 500, conCache, repositorio.consultas(), aciertos, fallos, tasa * 100);
        assertThat(repositorio.consultas()).isEqualTo((int) fallos);
        assertThat(tasa).isGreaterThan(0.8);
        assertThat(conCache).isLessThan(sinCache / 2);
        Long ttl = plantilla.getExpire("productos::1");
        anotar("TTL de productos::1 en Redis: %d s (spring.cache.redis.time-to-live: 10m)", ttl);
        assertThat(ttl).isBetween(590L, 600L);
    }

    @Test
    void paso2_dato_desactualizado_invalidacion_y_ttl_corto() throws Exception {
        assertThat(catalogo.buscar(5L).precio()).isEqualByComparingTo("1005");
        catalogo.actualizarPrecioSinInvalidar(5L, new BigDecimal("2000"));
        assertThat(catalogo.buscar(5L).precio()).as("desactualizado").isEqualByComparingTo("1005");

        catalogo.actualizarPrecio(5L, new BigDecimal("2000"));      // @CacheEvict
        assertThat(catalogo.buscar(5L).precio()).as("tras invalidar").isEqualByComparingTo("2000");

        assertThat(catalogo.buscarConTtlCorto(6L).precio()).isEqualByComparingTo("1006");
        catalogo.actualizarPrecioSinInvalidar(6L, new BigDecimal("3000"));
        assertThat(catalogo.buscarConTtlCorto(6L).precio()).as("dentro del TTL").isEqualByComparingTo("1006");
        Thread.sleep(1200);
        assertThat(catalogo.buscarConTtlCorto(6L).precio()).as("tras el TTL de 1 s").isEqualByComparingTo("3000");
    }

    int estampida(java.util.function.LongFunction<ProductoDto> buscar) throws Exception {
        buscar.apply(7L);                                   // la clave está en caché…
        plantilla.delete("productos-estampida::7");         // …y expira
        repositorio.reiniciarContador();
        ExecutorService pool = Executors.newFixedThreadPool(200);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<ProductoDto>> f = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            f.add(pool.submit(() -> {
                salida.await();
                return buscar.apply(7L);
            }));
        }
        salida.countDown();
        for (Future<ProductoDto> x : f) {
            assertThat(x.get().id()).isEqualTo(7L);
        }
        pool.shutdown();
        return repositorio.consultas();
    }

    @Test
    void paso3_estampida_de_200_peticiones_y_su_mitigacion() throws Exception {
        int sin = estampida(catalogo::buscarSinProteccion);
        int sync = estampida(catalogo::buscarSincronizado);
        int bloqueo = estampida(catalogo::buscarConBloqueoPorClave);
        anotar("Paso 3: 200 peticiones simultáneas al expirar la clave. Sin protección: %d consultas a la base. "
                + "Con @Cacheable(sync = true) sobre la caché de Redis por defecto: %d. Con bloqueo local por clave: %d", sin, sync, bloqueo);
        assertThat(sin).isGreaterThan(50);
        assertThat(sync).as("sync=true no protege con el RedisCacheWriter sin bloqueo").isGreaterThan(50);
        assertThat(bloqueo).isEqualTo(1);
    }

    @Test
    void paso3_entre_instancias_el_cache_writer_con_bloqueo_de_redis() throws Exception {
        // Dos "instancias" (dos RedisCacheManager independientes) con el escritor que bloquea en Redis.
        java.util.function.Supplier<org.springframework.cache.Cache> nueva = () -> {
            var gestor = org.springframework.data.redis.cache.RedisCacheManager
                    .builder(org.springframework.data.redis.cache.RedisCacheWriter.lockingRedisCacheWriter(redis)).build();
            return gestor.getCache("productos-distribuida");
        };
        var instanciaA = nueva.get();
        var instanciaB = nueva.get();
        repositorio.reiniciarContador();
        ExecutorService pool = Executors.newFixedThreadPool(200);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<ProductoDto>> f = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            var cache = i % 2 == 0 ? instanciaA : instanciaB;
            f.add(pool.submit(() -> {
                salida.await();
                return cache.get(8L, () -> repositorio.buscarDto(8L));
            }));
        }
        salida.countDown();
        for (Future<ProductoDto> x : f) {
            assertThat(x.get().id()).isEqualTo(8L);
        }
        pool.shutdown();
        anotar("Paso 3: 200 peticiones repartidas en dos instancias con RedisCacheWriter.lockingRedisCacheWriter: %d consultas a la base",
                repositorio.consultas());
        assertThat(repositorio.consultas()).isEqualTo(1);
    }

    @Test
    void paso4_autoinvocacion_no_pasa_por_la_cache() {
        catalogo.buscar(9L);
        catalogo.buscar(9L);
        catalogo.buscar(9L);
        int desdeFuera = repositorio.consultas();
        repositorio.reiniciarContador();
        catalogo.buscarVarios(List.of(9L, 9L, 9L));
        int desdeDentro = repositorio.consultas();
        anotar("Paso 4: tres llamadas a buscar(9) desde fuera: %d consulta(s). Las mismas tres desde buscarVarios (this.buscar): %d",
                desdeFuera, desdeDentro);
        assertThat(desdeFuera).isEqualTo(1);
        assertThat(desdeDentro).isEqualTo(3);     // aunque la clave ya estaba en caché
    }
}
