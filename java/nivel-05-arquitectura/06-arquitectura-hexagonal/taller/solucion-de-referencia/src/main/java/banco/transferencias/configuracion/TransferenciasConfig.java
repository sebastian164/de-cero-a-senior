package banco.transferencias.configuracion;

import banco.transferencias.adaptador.entrada.cli.TransferenciaCli;
import banco.transferencias.aplicacion.puerto.entrada.TransferirDinero;
import banco.transferencias.aplicacion.puerto.salida.CargarCuenta;
import banco.transferencias.aplicacion.puerto.salida.GuardarCuenta;
import banco.transferencias.aplicacion.puerto.salida.RegistroDeTransferencias;
import banco.transferencias.aplicacion.servicio.TransferirDineroService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Configuration
class TransferenciasConfig {
    @Bean Clock reloj() { return Clock.systemDefaultZone(); }

    /** La transacción se pone afuera del hexágono: envuelve al puerto de entrada. */
    @Bean
    TransferirDinero transferirDinero(CargarCuenta c, GuardarCuenta g, RegistroDeTransferencias r, Clock reloj, TransactionTemplate tx) {
        TransferirDinero servicio = new TransferirDineroService(c, g, r, reloj);
        return comando -> tx.execute(estado -> servicio.transferir(comando));
    }

    @Bean TransferenciaCli transferenciaCli(TransferirDinero t) { return new TransferenciaCli(t); }
}
