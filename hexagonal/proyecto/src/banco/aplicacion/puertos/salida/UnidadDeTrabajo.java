package banco.aplicacion.puertos.salida;

import java.util.function.Supplier;

/** Todo lo que ocurra dentro se confirma junto, o no ocurre. Es la sesión 04. */
public interface UnidadDeTrabajo {

    <T> T ejecutar(Supplier<T> trabajo);
}
