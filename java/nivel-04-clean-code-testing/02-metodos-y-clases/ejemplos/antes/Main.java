import java.util.List;

public class Main {

    public static void main(String[] args) {
        System.out.println(procesar(List.of("ana;34;CO", "luis;17;CO", ";40;MX", "eva;x;CO"), true));
    }

    static String procesar(List<String> lineas, boolean soloAdultos) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String linea : lineas) {
            String[] p = linea.split(";");
            if (p.length == 3) {
                if (!p[0].isBlank()) {
                    try {
                        int edad = Integer.parseInt(p[1]);
                        if (!soloAdultos || edad >= 18) {
                            sb.append(p[0].substring(0, 1).toUpperCase()).append(p[0].substring(1))
                              .append(" (").append(edad).append(") ").append(p[2]).append("\n");
                            n++;
                        }
                    } catch (NumberFormatException e) {
                        // ignorar
                    }
                }
            }
        }
        sb.append("Total: ").append(n);
        return sb.toString();
    }
}
