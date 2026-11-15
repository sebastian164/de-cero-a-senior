package fondo;

import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.util.function.Supplier;

/** Envuelve una unidad de trabajo real y, como haría JDBC, ocupa una conexión mientras dura. */
public final class UnidadDeTrabajoConFondo implements UnidadDeTrabajo {

    private final FondoDeConexiones fondo;
    private final UnidadDeTrabajo real;
    private final boolean seUneALaAbierta;
    private final ThreadLocal<Boolean> tieneConexion = ThreadLocal.withInitial(() -> false);

    public UnidadDeTrabajoConFondo(FondoDeConexiones fondo, UnidadDeTrabajo real, boolean seUneALaAbierta) {
        this.fondo = fondo;
        this.real = real;
        this.seUneALaAbierta = seUneALaAbierta;
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        if (seUneALaAbierta && tieneConexion.get()) {
            return real.ejecutar(trabajo);                       // ya hay una transacción: usa su conexión
        }
        fondo.tomar();                                           // aquí se espera si el pool está lleno
        boolean antes = tieneConexion.get();
        tieneConexion.set(true);
        try {
            return real.ejecutar(trabajo);
        } finally {
            tieneConexion.set(antes);
            fondo.devolver();                                    // pase lo que pase
        }
    }
}
