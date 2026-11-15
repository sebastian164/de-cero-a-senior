package banco.aplicacion;

import java.time.Instant;

/** Un hecho que ya ocurrió. El id es estable: quien lo reciba dos veces puede descartar la repetición. */
public record EventoDeCuenta(String id, String tipo, String cuentaId, long centavos, Instant ocurridoEn) {
}
