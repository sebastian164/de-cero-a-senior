package nosql.aplicacion;

import java.util.Optional;

/** Puerto de entrada de consulta: precio y existencias de un producto. Es el que decora la caché de la sesión 05. */
public interface ConsultarProducto {

    Optional<VistaDeProducto> porId(String producto);
}
