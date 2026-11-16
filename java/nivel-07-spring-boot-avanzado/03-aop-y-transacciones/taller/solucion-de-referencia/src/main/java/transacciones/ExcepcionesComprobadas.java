package transacciones;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExcepcionesComprobadas {
    public static class SaldoInsuficiente extends Exception {
        public SaldoInsuficiente(String m) { super(m); }
    }

    private final JdbcClient jdbc;
    public ExcepcionesComprobadas(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void conDefecto(String cliente) throws SaldoInsuficiente {
        jdbc.sql("INSERT INTO n703_pedidos(cliente, total) VALUES (?, 1)").param(cliente).update();
        throw new SaldoInsuficiente(cliente);                       // comprobada: NO revierte
    }

    @Transactional(rollbackFor = Exception.class)
    public void corregido(String cliente) throws SaldoInsuficiente {
        jdbc.sql("INSERT INTO n703_pedidos(cliente, total) VALUES (?, 1)").param(cliente).update();
        throw new SaldoInsuficiente(cliente);
    }
}
