package banco;

import static org.assertj.core.api.Assertions.assertThat;

import banco.apoyo.AlmacenDePrueba;
import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.reactiva.ServicioDeCuentas;
import banco.aplicacion.reactiva.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.reactiva.puertos.entrada.TransferirDinero.Resultado;
import banco.dominio.ReglaDeNegocioViolada;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** El servicio reactivo, sin Spring ni base de datos. Cada prueba tarda milisegundos y no depende de las demás. */
class ServicioDeCuentasTest {

    private AlmacenDePrueba almacen;
    private ServicioDeCuentas servicio;
    private String ana;
    private String luis;

    @BeforeEach
    void prepararDosCuentas() {
        almacen = new AlmacenDePrueba();
        servicio = new ServicioDeCuentas(almacen, almacen, almacen, almacen, () -> Instant.parse("2026-11-15T12:00:00Z"), almacen);
        ana = servicio.abrir("Ana", 100_000).map(VistaDeCuenta::id).block();     // block() en una prueba sí está bien
        luis = servicio.abrir("Luis", 0).map(VistaDeCuenta::id).block();
        almacen.transacciones.set(0);
    }

    @Test
    void sinSuscripcionNoOcurreNada() {
        servicio.transferir(new Orden("orden-1", ana, luis, 30_000));       // ensamblada y descartada

        assertThat(almacen.transacciones).hasValue(0);
        StepVerifier.create(servicio.porId(ana)).expectNextMatches(vista -> vista.saldoCentavos() == 100_000).verifyComplete();
    }

    @Test
    void transfiereUnaVezAunqueLaOrdenLlegueDosVeces() {
        Orden orden = new Orden("orden-1", ana, luis, 30_000);

        StepVerifier.create(servicio.transferir(orden)).expectNext(Resultado.REALIZADA).verifyComplete();
        StepVerifier.create(servicio.transferir(orden)).expectNext(Resultado.REPETIDA).verifyComplete();

        StepVerifier.create(servicio.porId(ana).map(VistaDeCuenta::saldoCentavos)).expectNext(70_000L).verifyComplete();
        StepVerifier.create(servicio.porId(luis).map(VistaDeCuenta::saldoCentavos)).expectNext(30_000L).verifyComplete();
        assertThat(almacen.cuantosEventos()).isEqualTo(1);
    }

    @Test
    void sinSaldoEsUnErrorDeNegocioYNoDejaRastro() {
        int movimientosAntes = almacen.cuantosMovimientos();

        StepVerifier.create(servicio.transferir(new Orden("orden-1", ana, luis, 999_999)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ReglaDeNegocioViolada.class)
                        .hasMessageContaining("saldo insuficiente"))
                .verify();

        assertThat(almacen.cuantosMovimientos()).isEqualTo(movimientosAntes);
        // La orden tampoco quedó registrada: se puede volver a intentar con saldo
        StepVerifier.create(servicio.transferir(new Orden("orden-1", ana, luis, 1_000))).expectNext(Resultado.REALIZADA).verifyComplete();
    }

    @Test
    void unConflictoSeReintentaDesdeElPrincipio() {
        almacen.conflictosPorFingir = 1;

        StepVerifier.create(servicio.transferir(new Orden("orden-1", ana, luis, 30_000)))
                .expectNext(Resultado.REALIZADA)                   // y no REPETIDA: el primer intento se revirtió entero
                .verifyComplete();

        assertThat(almacen.transacciones).hasValue(2);
    }

    @Test
    void tresConflictosSeguidosLleganAQuienLlama() {
        almacen.conflictosPorFingir = 3;

        StepVerifier.create(servicio.transferir(new Orden("orden-1", ana, luis, 30_000)))
                .expectError(ConflictoDeConcurrencia.class)
                .verify();

        assertThat(almacen.transacciones).hasValue(3);
    }
}
