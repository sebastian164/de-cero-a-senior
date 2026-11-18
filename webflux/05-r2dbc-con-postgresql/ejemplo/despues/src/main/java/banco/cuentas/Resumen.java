package banco.cuentas;

/** El informe de una cuenta. "hilo" dice en qué hilo se tuvo el resultado: es para la sesión, no para producción. */
public record Resumen(String id, String titular, long saldoCentavos, long movimientos, String hilo) {
}
