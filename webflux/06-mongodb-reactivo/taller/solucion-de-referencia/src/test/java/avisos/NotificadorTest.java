package avisos;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootTest(properties = {"modo=prueba", "spring.main.web-application-type=none"})
class NotificadorTest {
    @Autowired ReactiveMongoTemplate mongo;
    Confirmador confirmador;

    @BeforeEach void limpiar() {
        for (String c : List.of("pedidos", "avisos", "fichas")) mongo.dropCollection(c).block();
        mongo.createCollection("pedidos").block();
        confirmador = new Confirmador(mongo);
    }

    long avisos() { return mongo.getCollection("avisos").flatMap(c -> Mono.from(c.countDocuments())).block(); }
    long distintos() {
        return mongo.getCollection("avisos").flatMapMany(c -> c.distinct("pedidoId", String.class)).count().block();
    }
    static void esperar(BooleanSupplier condicion, String que) throws InterruptedException {
        for (int i = 0; i < 100 && !condicion.getAsBoolean(); i++) Thread.sleep(100);
        assertTrue(condicion.getAsBoolean(), "no se cumplió: " + que);
    }
    void crear(int desde, int hasta) {
        for (int i = desde; i <= hasta; i++) mongo.insert(new Document("_id", "p" + i).append("estado", "PENDIENTE"), "pedidos").block();
    }
    void cambiar(int desde, int hasta) {
        for (int i = desde; i <= hasta; i++) confirmador.cambiarUno("p" + i, i % 5 == 0 ? "RECHAZADO" : "CONFIRMADO").block();
    }

    @Test void unAvisoPorCambioTrasUnaDetencionOrdenada() throws Exception {
        crear(1, 30);
        Notificador n1 = new Notificador(mongo, true, 0, true); n1.prepararIndice().block();
        Disposable d = n1.flujo().subscribe(); Thread.sleep(1500);
        cambiar(1, 10);
        esperar(() -> avisos() == 10, "10 avisos");
        d.dispose();                                         // Ctrl+C ordenado
        cambiar(11, 20);                                     // cambios mientras está detenido
        Thread.sleep(500);
        assertEquals(10, avisos());
        Notificador n2 = new Notificador(mongo, true, 0, true);
        Disposable d2 = n2.flujo().subscribe(); Thread.sleep(1500);
        cambiar(21, 30);
        esperar(() -> avisos() == 30, "30 avisos tras reanudar");
        assertEquals(30, distintos(), "ni faltan ni sobran");
        d2.dispose();
    }

    @Test void unNotificadorDetenidoAntesDeSuPrimerEventoNoPierdeLoOcurridoMientrasEstuvoApagado() throws Exception {
        crear(1, 5);
        Notificador n1 = new Notificador(mongo, true, 0, true); n1.prepararIndice().block();
        Disposable d = n1.flujo().subscribe(); Thread.sleep(1500);
        d.dispose();                                         // se detuvo sin haber visto ningún evento: todavía no hay ficha, solo la hora de inicio
        cambiar(1, 5);                                       // todo ocurre mientras está apagado
        Notificador n2 = new Notificador(mongo, true, 0, true);
        Disposable d2 = n2.flujo().subscribe();
        esperar(() -> avisos() == 5, "los 5 avisos de lo ocurrido con el notificador apagado");
        d2.dispose();
    }

    /** Se cae justo después de insertar el aviso 3 y antes de guardar la ficha. */
    void caidaYReanudacion(boolean indiceUnico) throws Exception {
        crear(1, 6);
        Notificador n1 = new Notificador(mongo, indiceUnico, 3, true); n1.prepararIndice().block();
        n1.flujo().subscribe(a -> {}, e -> {}); Thread.sleep(1500);
        cambiar(1, 6);
        esperar(() -> avisos() >= 3, "al menos 3 avisos");
        Thread.sleep(500);
        Notificador n2 = new Notificador(mongo, indiceUnico, 0, true);
        Disposable d = n2.flujo().subscribe(); Thread.sleep(1500);
        esperar(() -> distintos() == 6, "los 6 pedidos con aviso");
        Thread.sleep(500);
        d.dispose();
    }

    @Test void sinIndiceUnicoUnaCaidaBruscaDuplicaElAviso() throws Exception {
        caidaYReanudacion(false);
        assertEquals(7, avisos(), "6 cambios y el aviso 3 repetido");
    }

    @Test void conIndiceUnicoUnaCaidaBruscaNoDuplica() throws Exception {
        caidaYReanudacion(true);
        assertEquals(6, avisos());
    }
}
