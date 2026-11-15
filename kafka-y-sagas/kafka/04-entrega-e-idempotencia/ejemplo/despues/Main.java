import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Un consumidor idempotente: recuerda qué mensajes aplicó, junto con el efecto. */
public class Main {

    record Abono(String id, String cuenta, int valor) {}

    // En un sistema real, estas dos estructuras son dos tablas de LA MISMA base de datos
    // y se escriben en LA MISMA transacción. Si no, entre una y otra cabe una caída.
    static final Map<String, Integer> saldos = new HashMap<>();
    static final Set<String> mensajesAplicados = new HashSet<>();

    public static void main(String[] args) {
        Abono a1 = new Abono("abono-1", "cuenta-7", 100);
        Abono a2 = new Abono("abono-2", "cuenta-7", 50);
        Abono a3 = new Abono("abono-3", "cuenta-7", 25);

        List<Abono> entregas = List.of(a1, a2, a2, a3, a1);   // dos repeticiones

        for (Abono abono : entregas) {
            aplicar(abono);
        }

        System.out.println();
        System.out.println("Saldo esperado: 175");
        System.out.println("Saldo real:     " + saldos.get("cuenta-7"));
    }

    static void aplicar(Abono abono) {
        if (!mensajesAplicados.add(abono.id())) {             // el identificador viene en el mensaje
            System.out.println("repetido " + abono.id() + ": se ignora");
            return;
        }
        saldos.merge(abono.cuenta(), abono.valor(), Integer::sum);
        System.out.println("aplicado " + abono.id() + " (+" + abono.valor() + ")");
    }
}
