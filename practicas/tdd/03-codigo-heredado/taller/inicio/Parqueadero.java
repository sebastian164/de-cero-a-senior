import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// Cobro del parqueadero. NO TOCAR sin hablar con Jorge (Jorge ya no trabaja aquí).
public class Parqueadero {

    static Map<String, LocalDateTime> entradas = new HashMap<>();
    static Map<String, String> tipos = new HashMap<>();
    static double caja = 0;

    public static void entrar(String placa, String tipo) {
        entradas.put(placa, LocalDateTime.now());
        tipos.put(placa, tipo);
        System.out.println("ENTRA " + placa);
    }

    public static double salir(String placa, boolean tieneSello) {
        LocalDateTime e = entradas.get(placa);
        LocalDateTime s = LocalDateTime.now();
        long m = Duration.between(e, s).toMinutes();
        double v = 0;
        String t = tipos.get(placa);
        if (t.equals("CARRO")) {
            if (m <= 15) {
                v = 0;
            } else {
                long h = m / 60;
                if (m % 60 > 0) h++;
                v = h * 4500;
                if (h > 8) v = 30000;
            }
        } else if (t.equals("MOTO")) {
            if (m <= 15) {
                v = 0;
            } else {
                long h = m / 60;
                if (m % 60 > 0) h++;
                v = h * 1500;
                if (h > 8) v = 10000;
            }
        } else {
            v = m * 50;
        }
        if (s.getDayOfWeek().getValue() == 7) v = v * 0.8;
        if (tieneSello && v > 0) {
            v = v - 3000;
            if (v < 0) v = 0;
        }
        if (s.getHour() >= 22 || s.getHour() < 6) v = v + 2000;
        caja = caja + v;
        entradas.remove(placa);
        tipos.remove(placa);
        System.out.println("SALE " + placa + " PAGA " + v);
        return v;
    }

    public static void main(String[] args) {
        entrar("ABC123", "CARRO");
        salir("ABC123", false);
        System.out.println("CAJA " + caja);
    }
}
