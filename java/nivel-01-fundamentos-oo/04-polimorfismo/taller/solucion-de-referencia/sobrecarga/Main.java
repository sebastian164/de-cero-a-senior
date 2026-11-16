import java.util.ArrayList;
import java.util.List;

/** Punto 5: la sobrecarga se resuelve con el tipo estático, en compilación. */
public class Main {

    static String tarifa(Object envio) { return "tarifa general"; }
    static String tarifa(String envio) { return "tarifa por código de envío"; }

    public static void main(String[] args) {
        Object codigo = "ENV-001";
        System.out.println(tarifa("ENV-001"));
        System.out.println(tarifa(codigo)); // sorpresa: el objeto ES un String, pero gana tarifa(Object)

        // Otra sorpresa clásica: remove(int) frente a remove(Object).
        List<Integer> pesos = new ArrayList<>(List.of(10, 20, 30));
        pesos.remove(1);                    // quita la posición 1 (el 20), no el valor 1
        System.out.println(pesos);
        pesos.remove(Integer.valueOf(10));  // ahora sí quita el valor 10
        System.out.println(pesos);
    }
}
