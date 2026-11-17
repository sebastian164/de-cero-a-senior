package tienda.adaptadores.salida.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/** Un DataSource sin base de datos: cada conexión anota lo que le hacen. Paso 3 del taller. */
final class ConexionesFalsas {

    final List<String> eventos = new ArrayList<>();
    int pedidas;
    int cerradas;
    boolean fallarAlRevertir;

    DataSource dataSource() {
        InvocationHandler deLaConexion = (proxy, metodo, argumentos) -> {
            if (fallarAlRevertir && metodo.getName().equals("rollback")) {
                eventos.add("rollback");
                throw new java.sql.SQLException("conexión rota");
            }
            switch (metodo.getName()) {
                case "setAutoCommit", "commit", "rollback" -> eventos.add(metodo.getName());
                case "close" -> {
                    eventos.add("close");
                    cerradas++;
                }
                case "isClosed" -> {
                    return cerradas >= pedidas;
                }
                default -> {
                }
            }
            return metodo.getReturnType() == boolean.class ? Boolean.FALSE : null;
        };
        InvocationHandler delPool = (proxy, metodo, argumentos) -> {
            if (metodo.getName().equals("getConnection")) {
                pedidas++;
                eventos.add("getConnection");
                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class}, deLaConexion);
            }
            throw new UnsupportedOperationException(metodo.getName());
        };
        return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {DataSource.class}, delPool);
    }
}
