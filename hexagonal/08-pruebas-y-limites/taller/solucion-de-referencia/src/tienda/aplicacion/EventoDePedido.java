package tienda.aplicacion;

import java.time.Instant;

/** Un hecho que ya ocurrió. El id es estable: quien lo reciba dos veces puede descartar la repetición. */
public record EventoDePedido(String id, String tipo, String pedidoId, String cliente, Instant ocurridoEn) {
}
