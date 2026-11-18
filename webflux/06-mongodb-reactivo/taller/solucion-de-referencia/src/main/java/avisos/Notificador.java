package avisos;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;
import org.bson.BsonDocument;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ChangeStreamEvent;
import org.springframework.data.mongodb.core.ChangeStreamOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Flujo de cambios filtrado en el servidor (solo updates que tocan "estado"). Por cada cambio: primero el aviso, DESPUES la ficha.
 * Si se cae entre las dos cosas, al reanudar llega el mismo evento otra vez y el índice único lo vuelve inofensivo.
 * En el orden contrario (ficha y luego aviso), una caída en medio PIERDE el aviso para siempre.
 */
public class Notificador {
    private final ReactiveMongoTemplate mongo;
    private final boolean indiceUnico;
    private final int paradaTrasAviso;
    private final AtomicInteger insertados = new AtomicInteger();
    /** Solo pruebas: simula una caída justo después de insertar el aviso N (antes de guardar la ficha). */
    public static class Caida extends RuntimeException { public Caida() { super("caída simulada"); } }
    private final boolean caidaComoExcepcion;

    /** Solo para poder matarlo con kill -9 desde fuera: pausa de pausaMs justo después de insertar el aviso N y antes de guardar la ficha. */
    private final int pausaTrasAviso; private final long pausaMs;
    private final AtomicInteger vistos = new AtomicInteger();
    private final AtomicReference<CompletableFuture<?>> enCurso = new AtomicReference<>();
    private volatile Disposable suscripcion;

    public Notificador(ReactiveMongoTemplate mongo, boolean indiceUnico, int paradaTrasAviso, boolean caidaComoExcepcion) {
        this(mongo, indiceUnico, paradaTrasAviso, caidaComoExcepcion, 0, 0);
    }

    public Notificador(ReactiveMongoTemplate mongo, boolean indiceUnico, int paradaTrasAviso, boolean caidaComoExcepcion, int pausaTrasAviso, long pausaMs) {
        this.mongo = mongo; this.indiceUnico = indiceUnico; this.paradaTrasAviso = paradaTrasAviso; this.caidaComoExcepcion = caidaComoExcepcion;
        this.pausaTrasAviso = pausaTrasAviso; this.pausaMs = pausaMs;
    }

    /** Arranca el flujo y devuelve de inmediato. Se detiene con detener(). */
    public void iniciar() {
        suscripcion = flujo().subscribe(a -> {}, e -> System.out.println("NOTIFICADOR FALLO: " + e));
    }

    /**
     * Detención ordenada: deja de pedir cambios y ESPERA a que el evento en curso termine (aviso y ficha). Cada evento corre en un
     * CompletableFuture que la cancelación del flujo no interrumpe: sin esto, un Ctrl+C entre el insert y la ficha repetiría el aviso.
     */
    public void detener() {
        if (suscripcion != null) suscripcion.dispose();
        CompletableFuture<?> f = enCurso.get();
        if (f != null) try { f.get(10, TimeUnit.SECONDS); } catch (Exception e) { System.out.println("NOTIFICADOR: el evento en curso no terminó: " + e); }
        System.out.println("NOTIFICADOR DETENIDO");
    }

    public Mono<Void> prepararIndice() {
        var ops = mongo.indexOps("avisos");
        return (indiceUnico
                ? ops.createIndex(new Index().on("pedidoId", Sort.Direction.ASC).on("estado", Sort.Direction.ASC).unique().named("unico_pedido_estado"))
                : ops.dropIndex("unico_pedido_estado").onErrorResume(e -> Mono.empty())).then();
    }

    /**
     * La ficha se lee al abrir; el flujo continúa hasta que se cancele. Si todavía no hay ficha (nunca llegó un evento), se guarda la hora de
     * inicio y se abre desde ahí: sin esto, un notificador detenido antes de su primer evento perdería todo lo ocurrido mientras estuvo apagado.
     * (La hora de inicio es el operationTime del servidor, no el reloj de este proceso.)
     */
    public Flux<Aviso> flujo() {
        return mongo.findById("notificador", Document.class, "fichas")
                .switchIfEmpty(Mono.defer(() -> mongo.executeCommand(new Document("ping", 1))
                        .flatMap(r -> mongo.save(new Document("_id", "notificador").append("inicio", r.get("operationTime")), "fichas"))))
                .flatMapMany(estado -> {
                    var o = ChangeStreamOptions.builder()
                            .filter(newAggregation(match(where("operationType").is("update").and("updateDescription.updatedFields.estado").exists(true))));
                    Document ficha = estado.get("ficha", Document.class);
                    if (ficha != null) o.resumeAfter(org.bson.BsonDocument.parse(ficha.toJson()));
                    else o.resumeAt(estado.get("inicio", org.bson.BsonTimestamp.class));
                    return mongo.changeStream("pedidos", o.build(), Document.class);
                })
                .concatMap(e -> {
                    CompletableFuture<Aviso> f = procesar(e).toFuture();              // arranca ya; cancelar el flujo no lo cancela
                    enCurso.set(f);
                    return Mono.fromFuture(f, true);
                });
    }

    Mono<Aviso> procesar(ChangeStreamEvent<Document> evento) {
        ChangeStreamDocument<Document> raw = evento.getRaw();
        BsonDocument campos = raw.getUpdateDescription().getUpdatedFields();
        String pedidoId = raw.getDocumentKey().getString("_id").getValue();
        Instant cambiado = Instant.ofEpochMilli(campos.getDateTime("cambiado").getValue());
        Aviso aviso = new Aviso(pedidoId, campos.getString("estado").getValue(), cambiado, Instant.now());
        BsonDocument token = evento.getResumeToken().asDocument();
        return mongo.insert(new Document("pedidoId", aviso.pedidoId()).append("estado", aviso.estado())
                        .append("cambiado", java.util.Date.from(aviso.cambiado())).append("recibido", java.util.Date.from(aviso.recibido())), "avisos")
                .onErrorResume(DuplicateKeyException.class, e -> Mono.empty())                       // ya estaba: idempotente
                .doOnNext(x -> {
                    if (paradaTrasAviso > 0 && insertados.incrementAndGet() == paradaTrasAviso) {
                        if (caidaComoExcepcion) throw new Caida();
                        Runtime.getRuntime().halt(137);                                              // como un kill -9
                    }
                })
                .then(Mono.defer(() -> pausaTrasAviso > 0 && vistos.incrementAndGet() == pausaTrasAviso
                        ? Mono.fromRunnable(() -> System.out.println("PAUSA tras el aviso " + pausaTrasAviso + ": ahora se puede matar")).then(Mono.delay(Duration.ofMillis(pausaMs))).then()
                        : Mono.<Void>empty()))
                .then(mongo.save(new Document("_id", "notificador").append("ficha", Document.parse(token.toJson())), "fichas"))
                .thenReturn(aviso);
    }
}
