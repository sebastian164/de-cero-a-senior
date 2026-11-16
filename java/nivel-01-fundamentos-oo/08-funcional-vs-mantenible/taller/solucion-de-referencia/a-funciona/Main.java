import java.util.HashMap;
import java.util.Map;

public class Main {
    static Map<String, String> tipos = new HashMap<>();
    static Map<String, Integer> entradas = new HashMap<>(); // minuto de entrada
    static Map<String, Boolean> residentes = new HashMap<>();
    static int carros = 0, motos = 0, bicis = 0;

    public static void main(String[] args) {
        entrar("ABC123", "CARRO", false, 0);
        System.out.println(salir("ABC123", 150));       // 3 horas: 5000 + 2*3000 = 11000
        entrar("XYZ12A", "MOTO", true, 0);
        System.out.println(salir("XYZ12A", 13 * 60));   // día moto 12000, residente 6000
        entrar("BICI1", "BICI", false, 0);
        System.out.println(salir("BICI1", 600));        // 0
        System.out.println(salir("NOEXISTE", 10));
    }

    static void entrar(String placa, String tipo, boolean residente, int minuto) {
        if (tipos.containsKey(placa)) { System.out.println("ya esta adentro"); return; }
        if (tipo.equals("CARRO")) { if (carros >= 20) { System.out.println("sin cupo"); return; } carros++; }
        else if (tipo.equals("MOTO")) { if (motos >= 30) { System.out.println("sin cupo"); return; } motos++; }
        else { if (bicis >= 15) { System.out.println("sin cupo"); return; } bicis++; }
        tipos.put(placa, tipo); entradas.put(placa, minuto); residentes.put(placa, residente);
    }

    static long salir(String placa, int minuto) {
        if (!tipos.containsKey(placa)) { System.out.println("no ha entrado"); return -1; }
        String tipo = tipos.remove(placa);
        int min = minuto - entradas.remove(placa);
        boolean res = residentes.remove(placa);
        int horas = (min + 59) / 60; if (horas == 0) horas = 1;
        long v = 0;
        if (tipo.equals("CARRO")) { carros--; v = horas > 12 ? 30000L * ((horas + 23) / 24) : 5000 + (horas - 1) * 3000L; }
        else if (tipo.equals("MOTO")) { motos--; v = horas > 12 ? 12000L * ((horas + 23) / 24) : 2000 + (horas - 1) * 1000L; }
        else { bicis--; v = 0; }
        if (res) v = v / 2;
        return v;
    }
}
