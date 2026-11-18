package pedidos.aplicacion.sincrona.puertos;

import java.util.function.Supplier;

public interface UnidadDeTrabajo { <T> T ejecutar(Supplier<T> trabajo); }
