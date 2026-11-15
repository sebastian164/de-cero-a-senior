import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        System.out.println(new Importador().importar(List.of("Teclado;120000", "Mouse;abc", "", "Monitor")));
    }
}

class Importador {

    List<String> importar(List<String> lineas) {
        List<String> resultado = new ArrayList<>();
        for (String linea : lineas) {
            try {
                String[] campos = linea.split(";");
                long precio = Long.parseLong(campos[1]);
                resultado.add(campos[0] + "=" + precio);
            } catch (Exception e) {
                // línea mala, seguimos
            }
        }
        return resultado.isEmpty() ? null : resultado;
    }
}
