package jpa;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.List;

/** Guarda cada sentencia SQL que Hibernate prepara, para contarlas en las pruebas. */
public class RegistroSql implements StatementInspector {

    private static final List<String> SENTENCIAS = new ArrayList<>();

    @Override
    public String inspect(String sql) {
        synchronized (SENTENCIAS) {
            SENTENCIAS.add(sql);
        }
        return sql;
    }

    public static void limpiar() {
        synchronized (SENTENCIAS) {
            SENTENCIAS.clear();
        }
    }

    public static List<String> sentencias() {
        synchronized (SENTENCIAS) {
            return List.copyOf(SENTENCIAS);
        }
    }

    public static long selects() {
        return sentencias().stream().filter(s -> s.trim().toLowerCase().startsWith("select")).count();
    }

    public static long updates() {
        return sentencias().stream().filter(s -> s.trim().toLowerCase().startsWith("update")).count();
    }
}
