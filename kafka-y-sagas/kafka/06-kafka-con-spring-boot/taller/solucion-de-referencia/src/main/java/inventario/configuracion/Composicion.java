package inventario.configuracion;

import inventario.adaptadores.entrada.kafka.LectorJackson;
import inventario.adaptadores.entrada.kafka.PedidosConfirmadosListener;
import inventario.adaptadores.salida.jdbc.ExistenciasJdbc;
import inventario.adaptadores.salida.kafka.PublicadorKafka;
import inventario.adaptadores.entrada.kafka.LectorDeEventos;
import inventario.aplicacion.Puertos.EventosProcesados;
import inventario.aplicacion.Puertos.Existencias;
import inventario.aplicacion.Puertos.PublicadorDeResultados;
import inventario.aplicacion.Puertos.Transaccion;
import inventario.aplicacion.ReservarInventario;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.Admin;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** La raíz de composición: el único lugar que conoce todas las piezas. El caso de uso no lleva anotaciones. */
@Configuration
public class Composicion {

    private static final String GRUPO = "inventario";
    private static final String TEMA = "pedidos";

    @Bean
    ExistenciasJdbc persistencia(JdbcTemplate jdbc) {
        return new ExistenciasJdbc(jdbc);
    }

    @Bean
    Transaccion transaccion(PlatformTransactionManager manager) {
        TransactionTemplate plantilla = new TransactionTemplate(manager);
        return new Transaccion() {
            @Override
            public <T> T ejecutar(Supplier<T> trabajo) {
                return plantilla.execute(estado -> trabajo.get());
            }
        };
    }

    @Bean
    PublicadorDeResultados publicador(KafkaTemplate<String, String> kafka) {
        return new PublicadorKafka(kafka);
    }

    @Bean
    ReservarInventario reservarInventario(ExistenciasJdbc persistencia, PublicadorDeResultados publicador, Transaccion transaccion) {
        return new ReservarInventario((EventosProcesados) persistencia, (Existencias) persistencia, publicador, transaccion);
    }

    @Bean(destroyMethod = "close")
    Admin adminDeMetricas(KafkaAdmin kafkaAdmin) {
        return Admin.create(kafkaAdmin.getConfigurationProperties());
    }

    // Registro propio porque este módulo solo trae micrometer-core (sin Actuator). Con Actuator habría dos MeterRegistry:
    // entonces se borra este bean y se inyecta el autoconfigurado.
    @Bean
    MeterRegistry registroDeMetricas() {
        return new SimpleMeterRegistry();
    }

    /** Paso 11: el servicio expone el retraso del grupo como métrica. */
    @Bean
    RetrasoDelGrupo retrasoDelGrupo(Admin adminDeMetricas, MeterRegistry registro) {
        return new RetrasoDelGrupo(adminDeMetricas, GRUPO, TEMA, registro);
    }

    @Bean
    LectorDeEventos lector() {
        return new LectorJackson();
    }

    @Bean
    @ConditionalOnProperty(name = "inventario.listener", havingValue = "bueno", matchIfMissing = true)
    PedidosConfirmadosListener pedidosConfirmadosListener(ReservarInventario casoDeUso, LectorDeEventos lector) {
        return new PedidosConfirmadosListener(casoDeUso, lector);
    }
}
