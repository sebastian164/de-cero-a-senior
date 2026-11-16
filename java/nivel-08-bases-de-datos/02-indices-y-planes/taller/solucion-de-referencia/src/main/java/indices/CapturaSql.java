package indices;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Guarda cada SQL que Hibernate envía, además del registro de org.hibernate.SQL. */
public class CapturaSql implements StatementInspector {
    public static final List<String> SENTENCIAS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        SENTENCIAS.add(sql);
        return sql;
    }
}
