package mensajeria.despacho;

import mensajeria.despacho.adaptadores.EnviosEnMemoria;
import mensajeria.despacho.adaptadores.MensajerosEnMemoria;
import mensajeria.despacho.aplicacion.AsignarMensajero;
import mensajeria.despacho.aplicacion.SolicitarEnvio;
import mensajeria.despacho.dominio.Cotizador;
import mensajeria.despacho.dominio.Envio;
import mensajeria.despacho.dominio.EstadoEnvio;
import mensajeria.despacho.dominio.Franja;
import mensajeria.despacho.dominio.Punto;
import mensajeria.despacho.dominio.ReglaDeDespacho;
import mensajeria.despacho.dominio.Tamano;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DespachoTest {

    static final LocalDateTime DIEZ = LocalDateTime.of(2030, 6, 3, 10, 0);
    static final Franja MANANA = new Franja(DIEZ, DIEZ.plusHours(2));
    static final Cotizador COTIZADOR = new Cotizador(Map.of("tienda-7", 15));

    EnviosEnMemoria envios;
    MensajerosEnMemoria mensajeros;
    List<String> ofertas;
    SolicitarEnvio solicitar;
    AsignarMensajero asignar;

    @BeforeEach
    void preparar() {
        envios = new EnviosEnMemoria();
        mensajeros = new MensajerosEnMemoria(envios);
        ofertas = new CopyOnWriteArrayList<>();
        solicitar = new SolicitarEnvio(envios, COTIZADOR);
        asignar = new AsignarMensajero(envios, mensajeros, (m, e) -> ofertas.add(m + "->" + e));
    }

    Envio pedir(Tamano t, Franja f) {
        return solicitar.ejecutar("c1", new Cotizador.Persona(), new Punto(0, 0), new Punto(3, 4), t, f);
    }

    // ---------- cotización ----------
    @Test
    void cotizaPorDistanciaTamanoFranjaYCliente() {
        Punto a = new Punto(0, 0), b = new Punto(3, 4);                       // 5 km
        long persona = COTIZADOR.cotizar(a, b, Tamano.PEQUENO, MANANA, new Cotizador.Persona());
        assertThat(persona).isEqualTo(4_000 + 5 * 1_200 + 2_000);            // 12.000
        Franja pico = new Franja(DIEZ.withHour(7), DIEZ.withHour(8));
        assertThat(COTIZADOR.cotizar(a, b, Tamano.PEQUENO, pico, new Cotizador.Persona())).isEqualTo(15_000);
        assertThat(COTIZADOR.cotizar(a, b, Tamano.PEQUENO, MANANA, new Cotizador.Comercio("tienda-7"))).isEqualTo(10_200);
        assertThat(COTIZADOR.cotizar(a, b, Tamano.PEQUENO, MANANA, new Cotizador.Comercio("otra"))).isEqualTo(12_000);
    }

    // ---------- estados ----------
    @Test
    void cicloDeVidaYTransicionesInvalidas() {
        mensajeros.alta("m1", 60, new Punto(1, 0));
        Envio e = pedir(Tamano.SOBRE, MANANA);
        assertThat(asignar.ejecutar(e.id())).contains("m1");
        Envio asignado = envios.buscar(e.id()).orElseThrow();
        assertThatThrownBy(asignado::entregar).isInstanceOf(ReglaDeDespacho.class);
        asignado.recoger();
        asignado.salirARuta();
        asignado.entregar();
        assertThat(asignado.historial()).containsExactly(
                EstadoEnvio.SOLICITADO, EstadoEnvio.ASIGNADO, EstadoEnvio.RECOGIDO, EstadoEnvio.EN_RUTA, EstadoEnvio.ENTREGADO);
        assertThatThrownBy(asignado::cancelar).isInstanceOf(ReglaDeDespacho.class);
    }

    // ---------- asignación ----------
    @Test
    void eligeElMasCercanoCompatible() {
        mensajeros.alta("lejos", 60, new Punto(4, 0));
        mensajeros.alta("cerca", 60, new Punto(0.5, 0));
        mensajeros.alta("fuera-de-radio", 60, new Punto(50, 50));
        assertThat(asignar.ejecutar(pedir(Tamano.MEDIANO, MANANA).id())).contains("cerca");
        assertThat(ofertas).hasSize(1);
    }

    @Test
    void noAsignaEnviosIncompatiblesPorCapacidad() {
        mensajeros.alta("moto", 30, new Punto(0, 0));
        assertThat(asignar.ejecutar(pedir(Tamano.MEDIANO, MANANA).id())).contains("moto");   // 20 L
        assertThat(asignar.ejecutar(pedir(Tamano.MEDIANO, MANANA).id())).isEmpty();          // 40 L > 30
        Franja tarde = new Franja(DIEZ.withHour(15), DIEZ.withHour(16));
        assertThat(asignar.ejecutar(pedir(Tamano.MEDIANO, tarde).id())).contains("moto");    // no se solapan
    }

    @Test
    void elRechazoDelMensajeroDevuelveElEnvioASolicitado() {
        mensajeros.alta("m1", 60, new Punto(0, 0));
        Envio e = pedir(Tamano.SOBRE, MANANA);
        asignar.ejecutar(e.id());
        var m = mensajeros.buscar("m1").orElseThrow();
        Envio cargado = m.activos().getFirst();
        m.rechazar(cargado);
        assertThat(cargado.estado()).isEqualTo(EstadoEnvio.SOLICITADO);
        assertThat(cargado.mensajeroId()).isNull();
    }

    /** Decisión difícil 1: dos asignaciones simultáneas al mismo mensajero no pueden pasar las dos. */
    @Test
    void asignacionesConcurrentesNoSuperanLaCapacidad() throws Exception {
        mensajeros.alta("unico", 30, new Punto(0, 0));
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 8; i++) ids.add(pedir(Tamano.MEDIANO, MANANA).id());   // cada uno 20 L; caben 1
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Optional<String>>> resultados = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (String id : ids) resultados.add(pool.submit(() -> { salida.await(); return asignar.ejecutar(id); }));
            salida.countDown();
        }
        long asignados = 0;
        for (var r : resultados) if (r.get().isPresent()) asignados++;
        assertThat(asignados).isEqualTo(1);
        assertThat(mensajeros.buscar("unico").orElseThrow().activos()).hasSize(1);
    }
}
