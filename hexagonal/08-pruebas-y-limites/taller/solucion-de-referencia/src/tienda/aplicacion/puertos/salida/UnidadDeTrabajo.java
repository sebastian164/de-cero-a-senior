package tienda.aplicacion.puertos.salida;

import java.util.function.Supplier;

/** Todo lo que ocurra dentro se confirma junto, o no ocurre. */
public interface UnidadDeTrabajo {

    <T> T ejecutar(Supplier<T> trabajo);
}
