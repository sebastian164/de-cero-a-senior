package tienda;

import java.util.function.Supplier;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;

/** Una unidad de trabajo que no hace nada: cada escritura queda guardada por su cuenta. */
public final class SinTransaccion implements UnidadDeTrabajo {

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return trabajo.get();
    }
}
