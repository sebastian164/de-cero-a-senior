package reservas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reservas.aplicacion.Puertos.*;
import reservas.aplicacion.ServicioReservas;
import reservas.aplicacion.ServicioReservas.Actor;
import reservas.dominio.Franja;
import reservas.dominio.Reserva;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Los casos de uso con un repositorio en memoria y un reloj fijo: reglas probadas sin infraestructura (ni Spring ni PostgreSQL). */
class ServicioReservasSinInfraestructuraTest {
    static final Instant AHORA = Instant.parse("2026-11-16T10:00:00Z");

    static Franja f(long desde, long hasta) { return new Franja(AHORA.plus(Duration.ofHours(desde)), AHORA.plus(Duration.ofHours(hasta))); }

    /** El repositorio de mentira respeta las mismas reglas que el real: nada de dos reservas activas solapadas. */
    static class EnMemoria implements RepositorioReservas {
        final Map<UUID, Reserva> reservas = new LinkedHashMap<>();
        final Map<String, UUID> claves = new HashMap<>();
        final List<Espera> espera = new ArrayList<>();
        long siguiente = 1;

        public void guardar(Reserva r) {
            if (reservas.values().stream().anyMatch(x -> x.chocaCon(r))) throw new Reserva.FranjaOcupada(r.espacio());
            reservas.put(r.id(), r);
        }
        public void actualizar(Reserva r) { reservas.put(r.id(), r); }
        public Optional<Reserva> buscar(UUID id) { return Optional.ofNullable(reservas.get(id)); }
        public List<Franja> ocupadas(String espacio, Instant desde, Instant hasta) {
            return reservas.values().stream().filter(r -> r.espacio().equals(espacio) && r.estado() == Reserva.Estado.ACTIVA
                    && r.franja().fin().isAfter(desde) && r.franja().inicio().isBefore(hasta)).map(Reserva::franja).toList();
        }
        public Pagina<Reserva> listar(String usuario, int pagina, int tamano) {
            var todas = reservas.values().stream().filter(r -> usuario == null || r.esDe(usuario)).toList();
            return new Pagina<>(todas.stream().skip((long) pagina * tamano).limit(tamano).toList(), pagina, tamano, todas.size());
        }
        public Optional<Reserva> porClave(String clave, String usuario) { return Optional.ofNullable(claves.get(clave + "|" + usuario)).map(reservas::get); }
        public void asociarClave(String clave, String usuario, UUID reserva) { claves.put(clave + "|" + usuario, reserva); }
        public void esperar(String espacio, String usuario, Franja franja) { espera.add(new Espera(espacio, usuario, franja, siguiente++)); }
        public Optional<Espera> primeroEnEspera(String espacio, Franja franja) {
            return espera.stream().filter(e -> e.espacio().equals(espacio) && e.franja().equals(franja)).findFirst();
        }
        public void quitarDeEspera(long id) { espera.removeIf(e -> e.id() == id); }
    }

    static final Transaccion SIN_TRANSACCION = new Transaccion() {
        public <T> T ejecutar(Supplier<T> trabajo) { return trabajo.get(); }
    };

    EnMemoria repo;
    ServicioReservas servicio;
    final Actor ana = new Actor("ana", false), beto = new Actor("beto", false), admin = new Actor("root", true);

    @BeforeEach
    void preparar() {
        repo = new EnMemoria();
        servicio = new ServicioReservas(repo, SIN_TRANSACCION, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void una_franja_ocupada_se_rechaza_y_una_contigua_se_acepta() {
        servicio.reservar(ana, null, "sala", f(10, 12));
        assertThatThrownBy(() -> servicio.reservar(beto, null, "sala", f(11, 13))).isInstanceOf(Reserva.FranjaOcupada.class);
        assertThat(servicio.reservar(beto, null, "sala", f(12, 14)).estado()).isEqualTo(Reserva.Estado.ACTIVA);
    }

    @Test
    void repetir_la_misma_clave_devuelve_la_misma_reserva_y_no_crea_otra() {
        Reserva a = servicio.reservar(ana, "k1", "sala", f(10, 11));
        Reserva b = servicio.reservar(ana, "k1", "sala", f(10, 11));
        assertThat(b.id()).isEqualTo(a.id());
        assertThat(repo.reservas).hasSize(1);
        assertThat(servicio.reservar(beto, "k1", "sala-2", f(10, 11)).id()).as("la clave es por usuario").isNotEqualTo(a.id());
    }

    @Test
    void cada_usuario_ve_y_cancela_solo_lo_suyo_y_el_admin_todo() {
        Reserva deAna = servicio.reservar(ana, null, "sala", f(40, 41));
        assertThatThrownBy(() -> servicio.ver(beto, deAna.id())).isInstanceOf(Reserva.SinPermiso.class);
        assertThatThrownBy(() -> servicio.cancelar(beto, deAna.id())).isInstanceOf(Reserva.SinPermiso.class);
        assertThat(servicio.ver(admin, deAna.id()).usuario()).isEqualTo("ana");
        assertThat(servicio.listar(beto, 0, 10).total()).isZero();
        assertThat(servicio.listar(admin, 0, 10).total()).isEqualTo(1);
        assertThatThrownBy(() -> servicio.ver(ana, UUID.randomUUID())).isInstanceOf(Reserva.NoEncontrada.class);
    }

    @Test
    void la_politica_de_cancelacion_se_aplica_con_el_reloj_del_servicio() {
        Reserva cercana = servicio.reservar(ana, null, "sala", f(20, 21));       // empieza en 20 h: ya pasó el plazo de 24 h
        assertThatThrownBy(() -> servicio.cancelar(ana, cercana.id())).isInstanceOf(Reserva.CancelacionFueraDePlazo.class);
        Reserva lejana = servicio.reservar(ana, null, "sala", f(30, 31));
        servicio.cancelar(ana, lejana.id());
        assertThat(servicio.ver(ana, lejana.id()).estado()).isEqualTo(Reserva.Estado.CANCELADA);
    }

    @Test
    void al_cancelar_la_franja_pasa_al_primero_de_la_lista_de_espera() {
        Reserva deAna = servicio.reservar(ana, null, "sala", f(30, 32));
        servicio.esperar(beto, "sala", f(30, 32));
        servicio.cancelar(ana, deAna.id());
        var activas = repo.reservas.values().stream().filter(r -> r.estado() == Reserva.Estado.ACTIVA).toList();
        assertThat(activas).singleElement().satisfies(r -> assertThat(r.usuario()).isEqualTo("beto"));
        assertThat(repo.espera).isEmpty();
    }

    @Test
    void la_disponibilidad_resta_lo_ocupado_de_la_ventana() {
        servicio.reservar(ana, null, "sala", f(2, 3));
        var d = servicio.disponibilidad("sala", AHORA, AHORA.plus(Duration.ofHours(5)));
        assertThat(d.ocupadas()).containsExactly(f(2, 3));
        assertThat(d.libres()).containsExactly(new Franja.Hueco(AHORA, f(2, 3).inicio()), new Franja.Hueco(f(2, 3).fin(), AHORA.plus(Duration.ofHours(5))));
    }
}
