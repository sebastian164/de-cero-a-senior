package banco.adaptadores.salida.mongo;

import banco.aplicacion.puertos.salida.UnidadDeTrabajo;
import java.util.function.Supplier;

/** "En MongoDB cada escritura ya es atómica, así que aquí no hay nada que hacer". */
public final class UnidadDeTrabajoMongo implements UnidadDeTrabajo {

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return trabajo.get();
    }
}
