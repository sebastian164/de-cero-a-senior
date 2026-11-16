package banco.transferencias.adaptador.salida.jpa;

import banco.transferencias.aplicacion.puerto.salida.CargarCuenta;
import banco.transferencias.aplicacion.puerto.salida.GuardarCuenta;
import banco.transferencias.aplicacion.puerto.salida.RegistroDeTransferencias;
import banco.transferencias.dominio.Cuenta;
import banco.transferencias.dominio.Dinero;
import banco.transferencias.dominio.NumeroCuenta;
import banco.transferencias.dominio.Transferencia;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/** Un adaptador puede implementar varios puertos. */
@Component
public class PersistenciaJpaAdapter implements CargarCuenta, GuardarCuenta, RegistroDeTransferencias {
    private final CuentaJpaRepository cuentas;
    private final TransferenciaJpaRepository transferencias;

    PersistenciaJpaAdapter(CuentaJpaRepository cuentas, TransferenciaJpaRepository transferencias) {
        this.cuentas = cuentas;
        this.transferencias = transferencias;
    }

    @Override
    public Optional<Cuenta> porNumero(NumeroCuenta numero) {
        return cuentas.findById(numero.valor())
                .map(e -> new Cuenta(new NumeroCuenta(e.numero), new Dinero(e.saldo), new Dinero(e.topeDiario)));
    }

    @Override
    public void guardar(Cuenta c) {
        cuentas.save(new CuentaJpaEntity(c.numero().valor(), c.saldo().centavos(), c.topeDiario().centavos()));
    }

    @Override
    public void registrar(Transferencia t) {
        transferencias.save(new TransferenciaJpaEntity(t.id(), t.origen().valor(), t.destino().valor(),
                t.monto().centavos(), t.fecha(), t.aceptada(), t.motivoRechazo()));
    }

    @Override
    public Dinero transferidoDesde(NumeroCuenta origen, LocalDate dia) {
        return new Dinero(transferencias.sumaAceptadas(origen.valor(), dia.atStartOfDay(), dia.plusDays(1).atStartOfDay()));
    }
}
