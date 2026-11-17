package tienda.adaptadores.salida.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Criterios del taller 06: "solo el adaptador de transacciones toca el pool" (búsqueda de getConnection) y
 * "el dominio y la aplicación no cambiaron" (ni se copiaron aquí: se compilan desde el servicio de pedidos del módulo hexagonal).
 */
class ArquitecturaDelPoolTest {

    private static final Path PEDIDOS = Path.of(System.getProperty("pedidos")).toAbsolutePath().normalize();

    @Test
    void soloLaUnidadDeTrabajoPideConexiones() throws IOException {
        assertEquals(List.of("UnidadDeTrabajoJdbc.java"), conTexto(Path.of("src/main/java"), "getConnection("));
        assertEquals(List.of(), conTexto(PEDIDOS.resolve("src"), "getConnection("), "el dominio y la aplicación no saben qué es una conexión");
        assertEquals(List.of(), conTexto(PEDIDOS.resolve("src/tienda/dominio"), "java.sql"));
        assertEquals(List.of(), conTexto(PEDIDOS.resolve("src/tienda/aplicacion"), "java.sql"));
    }

    @Test
    void elDominioYLaAplicacionNoSeCopiaronNiSeTocaronAqui() throws IOException {
        try (Stream<Path> archivos = Files.walk(Path.of("src"))) {
            List<String> paquetes = archivos.filter(a -> a.toString().endsWith(".java")).map(ArquitecturaDelPoolTest::paquete).distinct().sorted().toList();
            assertTrue(paquetes.stream().noneMatch(p -> p.equals("tienda.dominio") || p.equals("tienda.aplicacion") || p.startsWith("tienda.aplicacion.puertos")),
                    "el taller solo añade adaptadores y composición: " + paquetes);
        }
    }

    private static List<String> conTexto(Path raiz, String texto) throws IOException {
        try (Stream<Path> archivos = Files.walk(raiz)) {
            return archivos.filter(a -> a.toString().endsWith(".java")).filter(a -> leer(a).contains(texto))
                    .map(a -> a.getFileName().toString()).sorted().toList();
        }
    }

    private static String paquete(Path archivo) {
        return leer(archivo).lines().filter(l -> l.startsWith("package ")).findFirst().orElse("package ;").replace("package ", "").replace(";", "");
    }

    private static String leer(Path archivo) {
        try {
            return Files.readString(archivo);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }
}
