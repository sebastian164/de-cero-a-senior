package pruebas;

import inventario.aplicacion.Puertos.PublicadorDeResultados;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Decoradores del publicador para provocar fallos y medir. No son código del servicio. */
public class Defectos {

    public static final AtomicInteger LLAMADAS = new AtomicInteger();
    public static volatile long pausaMs = 0;
    public static volatile String pedidoQueExplota = null;

    /** Error de programación: una excepción inesperada para ese pedido. Cuenta cada ejecución y puede hacerla lenta. */
    @Configuration
    public static class Decorador {
        @Bean
        static BeanPostProcessor decoradorDelPublicador() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String nombre) {
                    if (!(bean instanceof PublicadorDeResultados real)) return bean;
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {PublicadorDeResultados.class}, (proxy, metodo, args) -> {
                        LLAMADAS.incrementAndGet();
                        if (pausaMs > 0) Thread.sleep(pausaMs);
                        if (pedidoQueExplota != null && args[1].toString().startsWith(pedidoQueExplota)) {
                            String nulo = null;
                            return nulo.length();                          // NullPointerException genuina
                        }
                        try {
                            return metodo.invoke(real, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
                }
            };
        }
    }
}
