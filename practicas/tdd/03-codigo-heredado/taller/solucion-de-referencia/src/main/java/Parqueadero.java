import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Cobro del parqueadero. NO TOCAR sin hablar con Jorge (Jorge ya no trabaja aquí).
public class Parqueadero {

    static final double SELLO = 3000;
    static final double RECARGO_NOCTURNO = 2000;
    static final double FACTOR_DOMINICAL = 0.8;

    static Map<String, LocalDateTime> entradas = new HashMap<>();
    static Map<String, String> tipos = new HashMap<>();
    static Set<String> electricos = new HashSet<>();
    static double caja = 0;

    public static void entrar(String placa, String tipo) {
        entrar(placa, tipo, LocalDateTime.now());
    }

    public static void entrar(String placa, String tipo, LocalDateTime cuando) {
        entrar(placa, tipo, false, cuando);
    }

    public static void entrar(String placa, String tipo, boolean electrico, LocalDateTime cuando) {
        entradas.put(placa, cuando);
        tipos.put(placa, tipo);
        if (electrico) {
            electricos.add(placa);
        }
        System.out.println("ENTRA " + placa);
    }

    public static double salir(String placa, boolean tieneSello) {
        return salir(placa, tieneSello, LocalDateTime.now());
    }

    public static double salir(String placa, boolean tieneSello, LocalDateTime salida) {
        LocalDateTime entrada = entradas.get(placa);
        long minutos = Duration.between(entrada, salida).toMinutes();
        double valor = cobro(tipos.get(placa), electricos.contains(placa), minutos, salida, tieneSello);
        caja = caja + valor;
        entradas.remove(placa);
        tipos.remove(placa);
        electricos.remove(placa);
        System.out.println("SALE " + placa + " PAGA " + valor);
        return valor;
    }

    static double cobro(String tipo, long minutos, LocalDateTime salida, boolean tieneSello) {
        return cobro(tipo, false, minutos, salida, tieneSello);
    }

    /** La mitad es del tiempo. El descuento dominical, el sello y el recargo nocturno se aplican después, como a todos. */
    static double cobro(String tipo, boolean electrico, long minutos, LocalDateTime salida, boolean tieneSello) {
        double valor = porTiempo(tipo, minutos);
        if (electrico) {
            valor = valor / 2;
        }
        valor = conDescuentoDominical(valor, salida);
        valor = conSello(valor, tieneSello);
        return valor + recargoNocturno(salida);
    }

    static double porTiempo(String tipo, long minutos) {
        return switch (tipo) {
            case "CARRO" -> porHoras(minutos, 4500, 30000);
            case "MOTO" -> porHoras(minutos, 1500, 10000);
            default -> minutos * 50;
        };
    }

    static final long MINUTOS_GRATIS = 15;

    /** Hora o fracción, después de los minutos gratis. Nunca pasa del tope. */
    static double porHoras(long minutos, double precioPorHora, double tope) {
        if (minutos <= MINUTOS_GRATIS) {
            return 0;
        }
        long horasOFraccion = (minutos + 59) / 60;
        return Math.min(horasOFraccion * precioPorHora, tope);
    }

    static double conDescuentoDominical(double valor, LocalDateTime salida) {
        return salida.getDayOfWeek() == DayOfWeek.SUNDAY ? valor * FACTOR_DOMINICAL : valor;
    }

    /** El sello descuenta 3000 sin bajar de cero, y solo si había algo que cobrar. */
    static double conSello(double valor, boolean tieneSello) {
        if (!tieneSello || valor <= 0) {
            return valor;
        }
        return Math.max(0, valor - SELLO);
    }

    /** Se mide a la hora de salida y se suma siempre, aunque el tiempo haya sido gratis (hallazgo 3). */
    static double recargoNocturno(LocalDateTime salida) {
        int hora = salida.getHour();
        return hora >= 22 || hora < 6 ? RECARGO_NOCTURNO : 0;
    }

    public static void main(String[] args) {
        entrar("ABC123", "CARRO");
        salir("ABC123", false);
        System.out.println("CAJA " + caja);
    }
}
