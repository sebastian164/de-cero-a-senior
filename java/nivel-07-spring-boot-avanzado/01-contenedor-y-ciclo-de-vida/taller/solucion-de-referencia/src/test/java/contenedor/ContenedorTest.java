package contenedor;

import contenedor.alcances.TiendaConProblema;
import contenedor.alcances.TiendaCorregida;
import contenedor.ciclo.Bitacora;
import fueradelescaneo.Circular;
import contenedor.notificadores.Consumidores;
import contenedor.pagos.PasarelaPagos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContenedorTest {

    static org.springframework.context.ConfigurableApplicationContext arrancar(String... args) {
        return new SpringApplicationBuilder(ContenedorApplication.class).run(args);
    }

    @Test
    void cuatroFormasDeResolverVariasImplementaciones() {
        try (var ctx = arrancar()) {
            var c = ctx.getBean(Consumidores.class);
            assertThat(c.porPrimary.canal()).isEqualTo("correo");
            assertThat(c.porQualifier.canal()).isEqualTo("sms");
            assertThat(c.todos).extracting(n -> n.canal()).containsExactlyInAnyOrder("correo", "sms", "push");
            assertThat(c.porNombre).containsOnlyKeys("correo", "sms", "push");
        }
    }

    @Test
    void fasesDelCicloDeVidaEnOrden() {
        Bitacora.FASES.clear();
        arrancar().close();
        assertThat(Bitacora.FASES).containsExactly(
                "1 constructor (con su dependencia)",
                "2 inyección por setter",
                "3 BeanNameAware: beanObservado",
                "4 BeanPostProcessor antes de inicializar (¿es proxy? false)",
                "5 @PostConstruct (¿es proxy? false)",
                "6 afterPropertiesSet",
                "7 BeanPostProcessor después de inicializar (¿es proxy? true)",
                "8 ApplicationReadyEvent",
                "9 @PreDestroy",
                "10 DisposableBean.destroy");
    }

    @Test
    void dependenciaCircularYSuRediseno() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(Circular.ConCiclo.class))
                .hasRootCauseInstanceOf(BeanCurrentlyInCreationException.class)
                .rootCause().hasMessageContaining("Requested bean is currently in creation: Is there an unresolvable circular reference");
        try (var ctx = new AnnotationConfigApplicationContext(Circular.SinCiclo.class)) {
            ctx.getBean(Circular.PedidosSinCiclo.class).confirmar("P-1");
            assertThat(ctx.getBean(Circular.FacturacionSinCiclo.class).facturados).containsExactly("P-1");
        }
    }

    @Test
    void prototypeEnSingletonYObjectProvider() {
        try (var ctx = arrancar()) {
            var conProblema = ctx.getBean(TiendaConProblema.class);
            conProblema.carritoNuevo().productos.add("leche");
            assertThat(conProblema.carritoNuevo().productos).containsExactly("leche");      // el siguiente cliente ve la leche
            assertThat(conProblema.carritoNuevo()).isSameAs(conProblema.carritoNuevo());

            var corregida = ctx.getBean(TiendaCorregida.class);
            corregida.carritoNuevo().productos.add("pan");
            assertThat(corregida.carritoNuevo().productos).isEmpty();
            assertThat(corregida.carritoNuevo()).isNotSameAs(corregida.carritoNuevo());
        }
    }

    @Test
    void beanCondicionalSeEligePorPropiedadSinRecompilar() {
        try (var ctx = arrancar()) {
            assertThat(ctx.getBean(PasarelaPagos.class).nombre()).isEqualTo("simulada");
        }
        try (var ctx = arrancar("--pagos.proveedor=stripe")) {
            assertThat(ctx.getBean(PasarelaPagos.class).nombre()).isEqualTo("stripe(sk_test_local)");
        }
    }

}
