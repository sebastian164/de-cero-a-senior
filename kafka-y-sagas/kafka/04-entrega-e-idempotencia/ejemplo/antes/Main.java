import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Un consumidor que aplica cada mensaje sin comprobar si ya lo había aplicado. */
public class Main {

    record Abono(String id, String cuenta, int valor) {}

    static final Map<String, Integer> saldos = new HashMap<>();

    public static void main(String[] args) {
        Abono a1 = new Abono("abono-1", "cuenta-7", 100);
        Abono a2 = new Abono("abono-2", "cuenta-7", 50);
        Abono a3 = new Abono("abono-3", "cuenta-7", 25);

        // Lo que entrega el broker con "al menos una vez": abono-2 llega dos veces
        // porque el consumidor se reinició después de procesarlo y antes de confirmarlo.
        List<Abono> entregas = List.of(a1, a2, a2, a3);

        for (Abono abono : entregas) {
            aplicar(abono);
        }

        System.out.println();
        System.out.println("Saldo esperado: 175");
        System.out.println("Saldo real:     " + saldos.get("cuenta-7"));
    }

    static void aplicar(Abono abono) {
        saldos.merge(abono.cuenta(), abono.valor(), Integer::sum);
        System.out.println("aplicado " + abono.id() + " (+" + abono.valor() + ")");
    }
}
