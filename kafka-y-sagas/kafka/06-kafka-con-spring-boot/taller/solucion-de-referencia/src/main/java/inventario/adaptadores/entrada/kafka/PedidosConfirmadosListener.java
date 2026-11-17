package inventario.adaptadores.entrada.kafka;

import inventario.aplicacion.ReservarInventario;
import inventario.dominio.PedidoConfirmado;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;

// El adaptador de entrada: traduce y delega. Nada más. (README de Kafka 06, "Después"; sin @Component: lo declara la raíz de composición)
public class PedidosConfirmadosListener {

    private final ReservarInventario casoDeUso;
    private final LectorDeEventos lector;

    public PedidosConfirmadosListener(ReservarInventario casoDeUso, LectorDeEventos lector) {
        this.casoDeUso = casoDeUso;
        this.lector = lector;
    }

    @KafkaListener(topics = "pedidos", groupId = "inventario", concurrency = "${inventario.concurrency:3}")
    void alRecibir(ConsumerRecord<String, String> registro) {
        PedidoConfirmado evento = lector.leer(registro.value(), PedidoConfirmado.class);   // lanza EventoInvalido
        casoDeUso.ejecutar(evento);                                                        // si falla, lanza
    }
}
