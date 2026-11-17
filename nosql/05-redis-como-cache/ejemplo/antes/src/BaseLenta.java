import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** El repositorio del banco, con la latencia de una base de datos ocupada: 50 ms por lectura. Cuenta las lecturas. */
final class BaseLenta implements RepositorioDeCuentas {

    private final RepositorioDeCuentas real;
    private final AtomicInteger lecturas = new AtomicInteger();

    BaseLenta(RepositorioDeCuentas real) {
        this.real = real;
    }

    @Override
    public String siguienteId() {
        return real.siguienteId();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        real.guardar(cuenta);
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        lecturas.incrementAndGet();
        try {
            Thread.sleep(50);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
        return real.buscar(id);
    }

    /** Devuelve las lecturas desde la última vez que se preguntó, y vuelve a empezar la cuenta. */
    int lecturasDesdeLaUltimaVez() {
        return lecturas.getAndSet(0);
    }
}
