package pruebas;

import inventario.adaptadores.entrada.kafka.LectorDeEventos;
import inventario.aplicacion.ReservarInventario;
import inventario.dominio.PedidoConfirmado;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;

/** El "antes" del README: el catch que no relanza. Misma lógica de negocio, mismo tema, mismo grupo. */
@Configuration
public class ListenerAntes {

    public static class Escucha {
        private final ReservarInventario casoDeUso;
        private final LectorDeEventos lector;

        Escucha(ReservarInventario casoDeUso, LectorDeEventos lector) {
            this.casoDeUso = casoDeUso;
            this.lector = lector;
        }

        @KafkaListener(topics = "pedidos", groupId = "inventario")
        public void escuchar(String mensaje) {
            try {
                casoDeUso.ejecutar(lector.leer(mensaje, PedidoConfirmado.class));
            } catch (Exception e) {
                System.out.println("ANTES: se atrapó y se siguió: " + e.getClass().getSimpleName());   // y se sigue como si nada
            }
        }
    }

    @Bean
    Escucha escuchaAntes(ReservarInventario casoDeUso, LectorDeEventos lector) {
        return new Escucha(casoDeUso, lector);
    }
}
