package banco.transferencias.aplicacion.servicio;

import banco.transferencias.aplicacion.puerto.entrada.TransferirDinero;
import banco.transferencias.aplicacion.puerto.salida.CargarCuenta;
import banco.transferencias.aplicacion.puerto.salida.GuardarCuenta;
import banco.transferencias.aplicacion.puerto.salida.RegistroDeTransferencias;
import banco.transferencias.dominio.Cuenta;
import banco.transferencias.dominio.Dinero;
import banco.transferencias.dominio.NumeroCuenta;
import banco.transferencias.dominio.ServicioDeTransferencias;
import banco.transferencias.dominio.Transferencia;
import banco.transferencias.dominio.TransferenciaRechazada;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

public class TransferirDineroService implements TransferirDinero {
    private final CargarCuenta cargar;
    private final GuardarCuenta guardar;
    private final RegistroDeTransferencias registro;
    private final Clock reloj;
    private final ServicioDeTransferencias reglas = new ServicioDeTransferencias();

    public TransferirDineroService(CargarCuenta cargar, GuardarCuenta guardar, RegistroDeTransferencias registro, Clock reloj) {
        this.cargar = cargar;
        this.guardar = guardar;
        this.registro = registro;
        this.reloj = reloj;
    }

    @Override
    public Transferencia transferir(Comando c) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        NumeroCuenta numOrigen = new NumeroCuenta(c.origen());
        NumeroCuenta numDestino = new NumeroCuenta(c.destino());
        Dinero monto = new Dinero(c.centavos());
        Transferencia resultado;
        try {
            Cuenta origen = cargar.porNumero(numOrigen).orElseThrow(() -> new TransferenciaRechazada("Cuenta de origen inexistente"));
            Cuenta destino = cargar.porNumero(numDestino).orElseThrow(() -> new TransferenciaRechazada("Cuenta de destino inexistente"));
            reglas.transferir(origen, destino, monto, registro.transferidoDesde(numOrigen, ahora.toLocalDate()));
            guardar.guardar(origen);
            guardar.guardar(destino);
            resultado = new Transferencia(UUID.randomUUID(), numOrigen, numDestino, monto, ahora, true, null);
        } catch (TransferenciaRechazada e) {
            resultado = new Transferencia(UUID.randomUUID(), numOrigen, numDestino, monto, ahora, false, e.getMessage());
        }
        registro.registrar(resultado);                    // toda transferencia queda registrada
        return resultado;
    }
}
