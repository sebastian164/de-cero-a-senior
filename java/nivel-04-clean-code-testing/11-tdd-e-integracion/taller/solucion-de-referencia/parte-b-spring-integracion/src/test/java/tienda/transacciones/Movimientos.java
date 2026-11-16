package tienda.transacciones;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Métodos transaccionales mínimos para comprobar lo que afirma la lección 07 sobre @Transactional. */
public class Movimientos {

    public static class ErrorComprobado extends Exception {
        private static final long serialVersionUID = 1L;
        public ErrorComprobado(String mensaje) { super(mensaje); }
    }

    private final JdbcTemplate jdbc;
    private final Movimientos interno;   // otro bean, para que la llamada pase por el proxy

    public Movimientos(JdbcTemplate jdbc, Movimientos interno) {
        this.jdbc = jdbc;
        this.interno = interno;
    }

    void insertar(String id) {
        jdbc.update("insert into movimiento(id) values (?)", id);
    }

    @Transactional
    public void insertarYLanzarComprobada(String id) throws ErrorComprobado {
        insertar(id);
        throw new ErrorComprobado("comprobada después de insertar " + id);
    }

    @Transactional(rollbackFor = ErrorComprobado.class)
    public void insertarYLanzarComprobadaConRollbackFor(String id) throws ErrorComprobado {
        insertar(id);
        throw new ErrorComprobado("comprobada después de insertar " + id);
    }

    @Transactional
    public void insertarYLanzarNoComprobada(String id) {
        insertar(id);
        throw new IllegalStateException("no comprobada después de insertar " + id);
    }

    /** Captura la excepción de un método transaccional de otro bean y sigue, como si nada. */
    @Transactional
    public void insertarYTragarseElFalloInterno(String id) {
        insertar(id);
        try {
            interno.insertarYLanzarNoComprobada(id + "-interno");
        } catch (IllegalStateException e) {
            // "no pasa nada, seguimos"
        }
    }
}
