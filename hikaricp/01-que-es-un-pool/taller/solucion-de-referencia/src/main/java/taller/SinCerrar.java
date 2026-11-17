package taller;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;

/** Paso 9: un proceso que abre un pool, lo usa y termina sin cerrarlo. */
public final class SinCerrar {

    private SinCerrar() {
    }

    public static void main(String[] args) throws Exception {
        HikariDataSource pool = new Pasos(args[0], args[1], args[2]).pool(10);
        try (Connection conexion = pool.getConnection()) {
            Pasos.seleccionarUno(conexion);
        }
        Thread.sleep(1_500);                                    // que el pool llegue a sus diez conexiones
        System.out.println("termino sin cerrar el pool");
        System.exit(0);                                         // sin pool.close()
    }
}
