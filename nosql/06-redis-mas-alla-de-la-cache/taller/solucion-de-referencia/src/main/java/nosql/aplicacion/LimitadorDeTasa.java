package nosql.aplicacion;

/** Puerto de salida: cuántas peticiones deja pasar cada cliente. Qué hace si su almacén falla lo decide el adaptador. */
public interface LimitadorDeTasa {

    record Decision(boolean permitido, long reintentarEnMs, boolean noDisponible) {
    }

    Decision intentar(String cliente);
}
