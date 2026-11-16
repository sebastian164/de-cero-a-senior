package tienda.pedidos.aplicacion;

import tienda.pedidos.dominio.Pedido;

/** Puerto de salida: lo define la aplicación, lo implementa la infraestructura. */
public interface RepositorioPedidos {
    Pedido guardar(Pedido pedido);
}
