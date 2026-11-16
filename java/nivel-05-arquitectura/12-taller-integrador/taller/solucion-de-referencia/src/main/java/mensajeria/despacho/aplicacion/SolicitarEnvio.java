package mensajeria.despacho.aplicacion;

import mensajeria.despacho.aplicacion.puertos.Envios;
import mensajeria.despacho.dominio.Cotizador;
import mensajeria.despacho.dominio.Envio;
import mensajeria.despacho.dominio.Franja;
import mensajeria.despacho.dominio.Punto;
import mensajeria.despacho.dominio.Tamano;

import java.util.UUID;

public class SolicitarEnvio {
    private final Envios envios;
    private final Cotizador cotizador;

    public SolicitarEnvio(Envios envios, Cotizador cotizador) { this.envios = envios; this.cotizador = cotizador; }

    public Envio ejecutar(String clienteId, Cotizador.TipoCliente tipo, Punto origen, Punto destino, Tamano tamano, Franja franja) {
        long precio = cotizador.cotizar(origen, destino, tamano, franja, tipo);
        Envio envio = new Envio(UUID.randomUUID().toString(), clienteId, origen, destino, tamano, franja, precio);
        envios.guardar(envio);
        return envio;
    }
}
