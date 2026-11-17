package tienda.integrador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Un solo artefacto, siete servicios: --tienda.servicio=pedidos|orquestador|inventario|pagos|envios|notificaciones|fidelidad.
 * Cada servicio es un hexágono en su propio paquete (dominio, aplicacion, adaptadores) con su raíz de composición en
 * configuracion/. Solo se activa la del servicio elegido; cada uno tiene su base (esquema s07_<servicio>) y su grupo de Kafka.
 */
@SpringBootApplication
@EnableScheduling
public class Integrador {
    public static void main(String[] args) {
        SpringApplication.run(Integrador.class, args);
    }
}
