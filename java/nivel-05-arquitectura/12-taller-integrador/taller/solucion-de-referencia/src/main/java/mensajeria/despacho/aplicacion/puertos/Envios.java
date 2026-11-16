package mensajeria.despacho.aplicacion.puertos;

import mensajeria.despacho.dominio.Envio;

import java.util.Optional;

public interface Envios {
    void guardar(Envio envio);
    Optional<Envio> buscar(String id);
}
