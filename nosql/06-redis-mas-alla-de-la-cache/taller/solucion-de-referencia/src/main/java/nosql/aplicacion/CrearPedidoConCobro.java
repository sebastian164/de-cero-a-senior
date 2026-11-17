package nosql.aplicacion;

import tienda.aplicacion.puertos.entrada.CrearPedido;
import tienda.dominio.Dinero;

/**
 * "Crear pedido" y cobrarlo. Primero el pedido (idempotente por su clave), después el cobro con la misma clave: si el proceso muere
 * después de cobrar y antes de guardar la respuesta, el reintento encuentra el pedido y vuelve a cobrar; que no cobre dos veces es cosa de la pasarela.
 */
public final class CrearPedidoConCobro implements CrearPedido {

    private final CrearPedido siguiente;
    private final PasarelaDePagos pasarela;
    private final Runnable despuesDeCobrar;

    public CrearPedidoConCobro(CrearPedido siguiente, PasarelaDePagos pasarela) {
        this(siguiente, pasarela, () -> {});
    }

    /** {@code despuesDeCobrar}: un gancho para las pruebas que matan el proceso entre el cobro y el guardado de la respuesta. */
    public CrearPedidoConCobro(CrearPedido siguiente, PasarelaDePagos pasarela, Runnable despuesDeCobrar) {
        this.siguiente = siguiente;
        this.pasarela = pasarela;
        this.despuesDeCobrar = despuesDeCobrar;
    }

    @Override
    public Resultado crear(Solicitud solicitud) {
        Resultado resultado = siguiente.crear(solicitud);
        pasarela.cobrar(solicitud.clave(), solicitud.cliente(), new Dinero(resultado.pedido().totalCentavos()));
        despuesDeCobrar.run();
        return resultado;
    }
}
