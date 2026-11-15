package banco;

import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.util.function.Supplier;

/** Una unidad de trabajo que no hace nada: cada escritura queda guardada por su cuenta. */
public final class SinTransaccion implements UnidadDeTrabajo {

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return trabajo.get();
    }
}
