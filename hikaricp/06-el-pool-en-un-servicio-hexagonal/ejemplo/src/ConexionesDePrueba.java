import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/** Un DataSource cuyas conexiones no hacen nada salvo anotar qué se les pidió. */
final class ConexionesDePrueba {

    private final List<String> anotado = new ArrayList<>();
    private int entregadas = 0;

    DataSource pool() {
        return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {DataSource.class},
                (proxy, metodo, argumentos) -> {
                    if (metodo.getName().equals("getConnection")) {
                        entregadas++;
                        anotado.add("tomar");
                        return conexion();
                    }
                    throw new UnsupportedOperationException(metodo.getName());
                });
    }

    private Connection conexion() {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, metodo, argumentos) -> {
                    switch (metodo.getName()) {
                        case "commit" -> anotado.add("confirmar");
                        case "rollback" -> anotado.add("revertir");
                        case "close" -> anotado.add("devolver");
                        default -> {
                            // setAutoCommit y lo demás: nada que anotar
                        }
                    }
                    return null;
                });
    }

    List<String> anotado() {
        return List.copyOf(anotado);
    }

    int entregadas() {
        return entregadas;
    }
}
