package banco;

import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import java.util.Optional;

/** Envuelve un repositorio real y falla al guardar una cuenta concreta: simula una caída a mitad de la operación. */
public final class RepositorioQueFalla implements RepositorioDeCuentas {

    private final RepositorioDeCuentas real;
    private String cuentaQueFalla;

    public RepositorioQueFalla(RepositorioDeCuentas real) {
        this.real = real;
    }

    public void fallarAlGuardar(String cuentaId) {
        this.cuentaQueFalla = cuentaId;
    }

    public void reparar() {
        this.cuentaQueFalla = null;
    }

    @Override
    public String siguienteId() {
        return real.siguienteId();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        if (cuenta.id().equals(cuentaQueFalla)) {
            throw new IllegalStateException("se perdió la conexión al guardar " + cuenta.id());
        }
        real.guardar(cuenta);
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        return real.buscar(id);
    }
}
