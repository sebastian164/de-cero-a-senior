package inventario.configuracion;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

/** Paso 11: el retraso del grupo como métrica (fin del tema menos posición confirmada, sumado). */
public final class RetrasoDelGrupo {

    private final Admin admin;
    private final String grupo;
    private final String tema;

    public RetrasoDelGrupo(Admin admin, String grupo, String tema, MeterRegistry registro) {
        this.admin = admin;
        this.grupo = grupo;
        this.tema = tema;
        Gauge.builder("kafka.consumer.group.lag", this, RetrasoDelGrupo::calcular).tag("group", grupo).strongReference(true).register(registro);
    }

    double calcular() {
        try {
            Map<TopicPartition, OffsetAndMetadata> confirmadas = admin.listConsumerGroupOffsets(grupo).partitionsToOffsetAndMetadata().get();
            Map<TopicPartition, OffsetSpec> pedirFin = new java.util.HashMap<>(), pedirInicio = new java.util.HashMap<>();
            admin.describeTopics(java.util.List.of(tema)).allTopicNames().get().get(tema).partitions()
                    .forEach(p -> { var tp = new TopicPartition(tema, p.partition()); pedirFin.put(tp, OffsetSpec.latest()); pedirInicio.put(tp, OffsetSpec.earliest()); });
            var fin = admin.listOffsets(pedirFin).all().get();
            var inicio = admin.listOffsets(pedirInicio).all().get();
            // una partición sin posición confirmada cuenta desde su inicio (auto-offset-reset=earliest)
            return pedirFin.keySet().stream().mapToLong(tp -> fin.get(tp).offset() - (confirmadas.containsKey(tp) ? confirmadas.get(tp).offset() : inicio.get(tp).offset())).sum();
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
