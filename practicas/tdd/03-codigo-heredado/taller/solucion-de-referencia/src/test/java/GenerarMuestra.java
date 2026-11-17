import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Genera la muestra de referencia: lo que el código hace HOY con cada combinación. */
public class GenerarMuestra {

    static final List<String> TIPOS = List.of("CARRO", "MOTO", "BICICLETA");
    // A cada lado de cada límite que se ve en el código: 15 minutos, cada hora, 6, 7 y 8 horas, un día.
    static final List<Long> MINUTOS = List.of(0L, 1L, 14L, 15L, 16L, 59L, 60L, 61L, 119L, 120L, 121L,
            360L, 361L, 420L, 421L, 479L, 480L, 481L, 540L, 541L, 1440L);
    // Sábado, domingo y lunes: a cada lado del domingo. Horas a cada lado de las 6:00 y de las 22:00.
    static final List<LocalDateTime> SALIDAS = salidas();
    static final List<Boolean> SELLOS = List.of(false, true);

    static List<LocalDateTime> salidas() {
        List<LocalDateTime> salidas = new ArrayList<>();
        for (int dia : List.of(14, 15, 16)) {
            for (int[] horaMinuto : new int[][] {{5, 59}, {6, 0}, {12, 0}, {21, 59}, {22, 0}}) {
                salidas.add(LocalDateTime.of(2026, 11, dia, horaMinuto[0], horaMinuto[1]));
            }
        }
        return salidas;
    }

    static String linea(String tipo, long minutos, LocalDateTime salida, boolean sello, double cobro) {
        return tipo + ";" + minutos + ";" + salida + ";" + sello + ";" + cobro;
    }

    public static void main(String[] args) throws IOException {
        List<String> lineas = new ArrayList<>();
        for (String tipo : TIPOS) {
            for (long minutos : MINUTOS) {
                for (LocalDateTime salida : SALIDAS) {
                    for (boolean sello : SELLOS) {
                        lineas.add(linea(tipo, minutos, salida, sello, Parqueadero.cobro(tipo, minutos, salida, sello)));
                    }
                }
            }
        }
        Files.write(Path.of(args[0]), lineas);
        System.out.println(lineas.size() + " combinaciones en " + args[0]);
    }
}
