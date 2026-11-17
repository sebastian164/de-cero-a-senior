package tienda;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tienda.Mensajes.MensajeIlegible;
import tienda.Mensajes.Orden;
import tienda.Mensajes.Respuesta;

/** Raíz de composición de los cuatro servicios. Lo que cambia entre ellos es qué beans se activan. */
@Configuration
public class Configuracion {

    @Bean
    TransactionTemplate plantilla(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }

    @Bean
    Salida salida(JdbcTemplate jdbc, TransactionTemplate tx, KafkaTemplate<String, String> kafka, @Value("${tienda.muere-al-publicar:0}") long muereAlPublicar) {
        Salida salida = new Salida(jdbc, tx, kafka);
        salida.muereAlPublicar = muereAlPublicar;
        return salida;
    }

    /** Política de errores: ilegible -> tema de fallidos sin reintentos; fallo pasajero -> reintentos con espera creciente. */
    @Bean
    DefaultErrorHandler manejadorDeErrores(KafkaTemplate<String, String> kafka) {
        var aFallidos = new DeadLetterPublishingRecoverer(kafka, (registro, error) -> new TopicPartition(Mensajes.FALLIDOS, -1));
        var espera = new ExponentialBackOffWithMaxRetries(6);
        espera.setInitialInterval(500);
        espera.setMultiplier(2);
        espera.setMaxInterval(4000);
        var manejador = new DefaultErrorHandler(aFallidos, espera);
        manejador.addNotRetryableExceptions(MensajeIlegible.class);
        return manejador;
    }

    /** Muerte programada para las pruebas de caídas: el proceso muere tras procesar el n-ésimo mensaje y antes de confirmar su posición. */
    static final AtomicLong PROCESADOS = new AtomicLong();

    static void morirSiToca(long tras) {
        if (tras > 0 && PROCESADOS.incrementAndGet() == tras) Runtime.getRuntime().halt(137);
    }

    // ------------------------------------------------------------------ orquestador
    @Configuration
    @ConditionalOnProperty(name = "tienda.servicio", havingValue = "orquestador")
    static class DelOrquestador {

        @Bean
        Orquestador orquestador(JdbcTemplate jdbc, TransactionTemplate tx, @Value("${tienda.espera-ms:3000}") long espera,
                                @Value("${tienda.intentos-de-compensacion:8}") int intentos) {
            return new Orquestador(jdbc, tx, espera, intentos);
        }

        @Bean
        AdminHttp adminHttp(Orquestador orquestador, Salida salida, JdbcTemplate jdbc, @Value("${tienda.puerto:0}") int puerto) throws java.io.IOException {
            return new AdminHttp(orquestador, salida, jdbc, puerto);
        }

        @Bean
        EscuchaDeRespuestas escucha(Orquestador orquestador, @Value("${tienda.muere-tras:0}") long muereTras) {
            return new EscuchaDeRespuestas(orquestador, muereTras);
        }
    }

    static class EscuchaDeRespuestas {
        private final Orquestador orquestador;
        private final long muereTras;

        EscuchaDeRespuestas(Orquestador orquestador, long muereTras) {
            this.orquestador = orquestador;
            this.muereTras = muereTras;
        }

        @KafkaListener(topics = Mensajes.RESPUESTAS, groupId = "orquestador", concurrency = "${tienda.concurrencia:3}")
        void alRecibir(ConsumerRecord<String, String> registro) {
            orquestador.atender(Respuesta.leer(registro.value()));
            morirSiToca(muereTras);
        }
    }

    // ------------------------------------------------------------------ participantes
    @Configuration
    @ConditionalOnProperty(name = "tienda.servicio", havingValue = "inventario")
    static class DeInventario {
        @Bean
        Inventario inventario(JdbcTemplate jdbc, TransactionTemplate tx, @Value("${tienda.falla-compensacion:false}") boolean falla) {
            Inventario i = new Inventario(jdbc, tx);
            i.fallarSiempreLaCompensacion = falla;
            return i;
        }

        @Bean
        EscuchaDeOrdenes escuchaInventario(Inventario inventario, @Value("${tienda.muere-tras:0}") long muereTras) {
            return new EscuchaDeOrdenes(inventario, muereTras) {
                @Override
                @KafkaListener(topics = Mensajes.ORDENES_INVENTARIO, groupId = "inventario", concurrency = "${tienda.concurrencia:3}")
                void alRecibir(ConsumerRecord<String, String> registro) {
                    super.alRecibir(registro);
                }
            };
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "tienda.servicio", havingValue = "pagos")
    static class DePagos {
        @Bean
        Pagos pagos(JdbcTemplate jdbc, TransactionTemplate tx) {
            return new Pagos(jdbc, tx);
        }

        @Bean
        EscuchaDeOrdenes escuchaPagos(Pagos pagos, @Value("${tienda.muere-tras:0}") long muereTras) {
            return new EscuchaDeOrdenes(pagos, muereTras) {
                @Override
                @KafkaListener(topics = Mensajes.ORDENES_PAGOS, groupId = "pagos", concurrency = "${tienda.concurrencia:3}")
                void alRecibir(ConsumerRecord<String, String> registro) {
                    super.alRecibir(registro);
                }
            };
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "tienda.servicio", havingValue = "envios")
    static class DeEnvios {
        @Bean
        Envios envios(JdbcTemplate jdbc, TransactionTemplate tx) {
            return new Envios(jdbc, tx);
        }

        @Bean
        EscuchaDeOrdenes escuchaEnvios(Envios envios, @Value("${tienda.muere-tras:0}") long muereTras) {
            return new EscuchaDeOrdenes(envios, muereTras) {
                @Override
                @KafkaListener(topics = Mensajes.ORDENES_ENVIOS, groupId = "envios", concurrency = "${tienda.concurrencia:3}")
                void alRecibir(ConsumerRecord<String, String> registro) {
                    super.alRecibir(registro);
                }
            };
        }
    }

    static class EscuchaDeOrdenes {
        private final Participante participante;
        private final long muereTras;

        EscuchaDeOrdenes(Participante participante, long muereTras) {
            this.participante = participante;
            this.muereTras = muereTras;
        }

        void alRecibir(ConsumerRecord<String, String> registro) {
            participante.atender(Orden.leer(registro.value()));
            morirSiToca(muereTras);
        }
    }

    /** Crea las tablas del servicio al arrancar (schema.sql común + el específico). */
    @Bean
    Esquema esquema(DataSource ds, @Value("${tienda.servicio}") String servicio) {
        return new Esquema(new JdbcTemplate(ds), servicio);
    }

    static final class Esquema {
        Esquema(JdbcTemplate jdbc, String servicio) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS salida(id uuid PRIMARY KEY, seq bigserial UNIQUE, tema text NOT NULL, clave text NOT NULL, payload text NOT NULL, "
                    + "creada timestamptz DEFAULT clock_timestamp(), publicada timestamptz)");
            switch (servicio) {
                case "orquestador" -> {
                    jdbc.execute("CREATE TABLE IF NOT EXISTS procesados(id text PRIMARY KEY)");
                    jdbc.execute("CREATE TABLE IF NOT EXISTS sagas(id text PRIMARY KEY, correlacion text, estado text NOT NULL, version bigint NOT NULL DEFAULT 0, centavos bigint, producto text, cantidad int, ciudad text, "
                            + "orden_pendiente text, destino text, intentos int NOT NULL DEFAULT 0, ultimo_cambio timestamptz, motivo text, revision boolean NOT NULL DEFAULT false)");
                }
                case "inventario" -> {
                    jdbc.execute("CREATE TABLE IF NOT EXISTS respuestas(orden_id text PRIMARY KEY, tipo text NOT NULL)");
                    jdbc.execute("CREATE TABLE IF NOT EXISTS stock(producto text PRIMARY KEY, disponibles int NOT NULL CHECK (disponibles >= 0))");
                    jdbc.execute("CREATE TABLE IF NOT EXISTS reservas(saga_id text PRIMARY KEY, producto text, cantidad int, liberada boolean NOT NULL DEFAULT false)");
                    jdbc.execute("CREATE TABLE IF NOT EXISTS lapidas(saga_id text PRIMARY KEY)");
                }
                case "pagos" -> {
                    jdbc.execute("CREATE TABLE IF NOT EXISTS respuestas(orden_id text PRIMARY KEY, tipo text NOT NULL)");
                    jdbc.execute("CREATE TABLE IF NOT EXISTS cobros(saga_id text PRIMARY KEY, centavos bigint, reembolsado boolean NOT NULL DEFAULT false)");
                    jdbc.execute("CREATE TABLE IF NOT EXISTS lapidas(saga_id text PRIMARY KEY)");
                }
                case "envios" -> {
                    jdbc.execute("CREATE TABLE IF NOT EXISTS respuestas(orden_id text PRIMARY KEY, tipo text NOT NULL)");
                    jdbc.execute("CREATE TABLE IF NOT EXISTS despachos(saga_id text PRIMARY KEY, ciudad text)");
                }
                default -> { /* los servicios del integrador (sesión 07) crean sus propias tablas */ }
            }
        }
    }

    static Map<String, String> nada() {
        return Map.of();
    }
}
