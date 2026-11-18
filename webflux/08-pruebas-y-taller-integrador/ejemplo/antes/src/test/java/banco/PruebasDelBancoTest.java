package banco;

import static org.junit.jupiter.api.Assertions.assertEquals;

import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.reactiva.ServicioDeCuentas;
import banco.aplicacion.reactiva.puertos.entrada.TransferirDinero.Orden;
import banco.arranque.AplicacionBanco;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Tres pruebas que pasan. Ninguna demuestra lo que dice su nombre. */
@SpringBootTest(classes = AplicacionBanco.class)
class PruebasDelBancoTest {

    @Autowired
    ServicioDeCuentas servicio;

    @Test
    void abrirUnaCuenta() {
        VistaDeCuenta ana = servicio.abrir("Ana", 100_000).block();

        assertEquals(100_000, ana.saldoCentavos());
    }

    @Test
    void consultarUnaCuenta() {
        VistaDeCuenta ana = servicio.abrir("Ana", 100_000).block();

        servicio.porId(ana.id())
                .subscribe(vista -> assertEquals(999, vista.saldoCentavos()));   // 999: está mal a propósito
    }

    @Test
    void transferir() throws InterruptedException {
        VistaDeCuenta ana = servicio.abrir("Ana", 100_000).block();
        VistaDeCuenta luis = servicio.abrir("Luis", 0).block();

        servicio.transferir(new Orden("orden-1", ana.id(), luis.id(), 30_000)).subscribe();
        Thread.sleep(2_000);                                       // "para que le dé tiempo"

        assertEquals(70_000, servicio.porId(ana.id()).block().saldoCentavos());
    }
}
