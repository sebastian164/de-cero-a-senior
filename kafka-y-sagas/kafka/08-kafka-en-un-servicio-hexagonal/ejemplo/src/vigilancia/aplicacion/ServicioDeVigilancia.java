package vigilancia.aplicacion;

import java.time.LocalDate;
import java.time.ZoneOffset;
import vigilancia.aplicacion.PuertosDeSalida.EnviosPorCuenta;
import vigilancia.aplicacion.PuertosDeSalida.EventosVistos;
import vigilancia.aplicacion.PuertosDeSalida.Notificador;
import vigilancia.dominio.EnviosDelDia;

/**
 * El caso de uso. En un servicio con base de datos, lo que hay dentro de registrar va en una unidad de
 * trabajo, como en el banco: marcar el evento como visto y sumar el envío se confirman juntos o no ocurren.
 */
public final class ServicioDeVigilancia implements RegistrarTransferencia {

    private final EnviosPorCuenta envios;
    private final EventosVistos vistos;
    private final Notificador notificador;

    public ServicioDeVigilancia(EnviosPorCuenta envios, EventosVistos vistos, Notificador notificador) {
        this.envios = envios;
        this.vistos = vistos;
        this.notificador = notificador;
    }

    @Override
    public Resultado registrar(Transferencia transferencia) {
        if (!vistos.registrarSiEsNuevo(transferencia.idDelEvento())) {
            return Resultado.REPETIDA;                           // el transporte entrega al menos una vez: aquí se descarta
        }
        LocalDate dia = LocalDate.ofInstant(transferencia.ocurridaEn(), ZoneOffset.UTC);
        EnviosDelDia delDia = envios.buscar(transferencia.cuentaId(), dia)
                .orElseGet(() -> new EnviosDelDia(transferencia.cuentaId(), dia, 0));

        boolean cruzoElUmbral = delDia.registrar(transferencia.centavos());   // la regla, en el dominio

        envios.guardar(delDia);
        if (cruzoElUmbral) {
            notificador.avisar(delDia.cuentaId(), delDia.totalCentavos());
        }
        return Resultado.REGISTRADA;
    }
}
