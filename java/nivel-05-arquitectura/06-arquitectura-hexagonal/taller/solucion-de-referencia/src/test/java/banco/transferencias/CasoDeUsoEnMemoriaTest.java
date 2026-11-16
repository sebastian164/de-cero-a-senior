package banco.transferencias;

import banco.transferencias.adaptador.entrada.cli.TransferenciaCli;
import banco.transferencias.adaptador.salida.memoria.CuentasEnMemoria;
import banco.transferencias.adaptador.salida.memoria.RegistroEnMemoria;
import banco.transferencias.aplicacion.puerto.entrada.TransferirDinero;
import banco.transferencias.aplicacion.servicio.TransferirDineroService;
import banco.transferencias.dominio.Cuenta;
import banco.transferencias.dominio.Dinero;
import banco.transferencias.dominio.NumeroCuenta;
import banco.transferencias.dominio.Transferencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** El caso de uso completo, solo con adaptadores en memoria. La prueba es un adaptador primario más. */
class CasoDeUsoEnMemoriaTest {

    static final NumeroCuenta ANA = new NumeroCuenta("1001");
    static final NumeroCuenta LUIS = new NumeroCuenta("2002");

    CuentasEnMemoria cuentas;
    RegistroEnMemoria registro;
    MutableClock reloj;
    TransferirDinero transferir;

    static class MutableClock extends Clock {
        Instant ahora = Instant.parse("2030-01-01T10:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    @BeforeEach
    void preparar() {
        cuentas = new CuentasEnMemoria();
        registro = new RegistroEnMemoria();
        reloj = new MutableClock();
        cuentas.guardar(new Cuenta(ANA, Dinero.pesos(1_000), Dinero.pesos(500)));
        cuentas.guardar(new Cuenta(LUIS, Dinero.pesos(0), Dinero.pesos(500)));
        transferir = new TransferirDineroService(cuentas, cuentas, registro, reloj);
    }

    Transferencia mover(NumeroCuenta de, NumeroCuenta a, long pesos) {
        return transferir.transferir(new TransferirDinero.Comando(de.valor(), a.valor(), pesos * 100));
    }

    @Test
    void transfiereYRegistra() {
        assertThat(mover(ANA, LUIS, 300).aceptada()).isTrue();
        assertThat(cuentas.porNumero(ANA).orElseThrow().saldo()).isEqualTo(Dinero.pesos(700));
        assertThat(cuentas.porNumero(LUIS).orElseThrow().saldo()).isEqualTo(Dinero.pesos(300));
        assertThat(registro.todas()).hasSize(1);
    }

    @Test
    void saldoInsuficiente() {
        cuentas.guardar(new Cuenta(ANA, Dinero.pesos(100), Dinero.pesos(500)));
        Transferencia t = mover(ANA, LUIS, 101);
        assertThat(t.aceptada()).isFalse();
        assertThat(t.motivoRechazo()).isEqualTo("Saldo insuficiente");
        assertThat(cuentas.porNumero(ANA).orElseThrow().saldo()).isEqualTo(Dinero.pesos(100));
    }

    @Test
    void topeDiarioAcumulaYSeReiniciaAlDiaSiguiente() {
        assertThat(mover(ANA, LUIS, 300).aceptada()).isTrue();
        assertThat(mover(ANA, LUIS, 200).aceptada()).isTrue();         // 500 exacto: permitido
        Transferencia excedida = mover(ANA, LUIS, 1);
        assertThat(excedida.motivoRechazo()).isEqualTo("Supera el tope diario");
        reloj.ahora = reloj.ahora.plusSeconds(24 * 3600);
        assertThat(mover(ANA, LUIS, 1).aceptada()).isTrue();
    }

    @Test
    void noSeTransfiereALaMismaCuenta() {
        assertThat(mover(ANA, ANA, 1).motivoRechazo()).isEqualTo("No se transfiere a la misma cuenta");
    }

    @Test
    void losRechazosTambienQuedanRegistrados() {
        mover(ANA, ANA, 1);
        mover(ANA, LUIS, 5_000);
        mover(ANA, new NumeroCuenta("9999"), 1);
        assertThat(registro.todas()).extracting(Transferencia::motivoRechazo)
                .containsExactly("No se transfiere a la misma cuenta", "Saldo insuficiente", "Cuenta de destino inexistente");
    }

    @Test
    void elAdaptadorCliUsaElMismoPuerto() {
        var salida = new ByteArrayOutputStream();
        int codigo = new TransferenciaCli(transferir).ejecutar(new String[]{"1001", "2002", "1000"}, new PrintStream(salida));
        assertThat(codigo).isZero();
        assertThat(salida.toString()).startsWith("OK ");
        codigo = new TransferenciaCli(transferir).ejecutar(new String[]{"1001", "1001", "1000"}, new PrintStream(salida));
        assertThat(codigo).isEqualTo(1);
        assertThat(salida.toString()).contains("RECHAZADA: No se transfiere a la misma cuenta");
    }
}
