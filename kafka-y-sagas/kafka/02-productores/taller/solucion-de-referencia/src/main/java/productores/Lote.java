package productores;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Proceso hijo del paso 9. Envía exactamente N mensajes con linger.ms=60000 (los lotes esperan en memoria, nada
 * sale por tiempo), anota "N listo" en un archivo y se queda dormido sin cerrar el productor. Así el padre sabe
 * con exactitud cuántos send() aceptó y el kill nunca compite con un vaciado.
 * Modo "ordenado": un gancho de apagado cierra el productor (SIGTERM) y vacía lo pendiente.
 * Modo "matar": nadie cierra nada; el padre lo mata con SIGKILL.
 */
public final class Lote {

    public static void main(String[] args) throws Exception {
        String tema = args[0];
        long total = Long.parseLong(args[1]);
        boolean ordenado = args[2].equals("ordenado");
        Path progreso = Path.of(args[3]);
        ProductorPedidos p = new ProductorPedidos(comun.Kf.bootstrap(), tema, "linger.ms", "60000", "batch.size", "524288",
                "delivery.timeout.ms", "120000");
        if (ordenado) {
            Runtime.getRuntime().addShutdownHook(new Thread(p::close));   // vacía lo pendiente antes de salir
        }
        for (long i = 1; i <= total; i++) {
            p.publicar(i, "cliente-" + (i % 7));
        }
        Files.writeString(progreso, total + " listo\n");
        Thread.sleep(600_000);                                         // espera a que el padre lo mate o lo apague
    }
}
