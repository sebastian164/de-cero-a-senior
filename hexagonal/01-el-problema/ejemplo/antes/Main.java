import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** Transferencias entre cuentas, todo en un método. Funciona. */
public class Main {

    static Map<String, Long> saldos = new HashMap<>();
    static Map<String, Long> transferidoHoy = new HashMap<>();
    static LocalDate dia = LocalDate.now();

    public static void main(String[] args) throws IOException {
        saldos.put("ana", 100_000_000L);                    // un millón de pesos, en centavos
        saldos.put("luis", 20_000_000L);
        String[] ordenes = {
            "transferir ana luis 30000000",
            "transferir luis ana 90000000",
            "transferir ana ana 100000",
            "transferir ana luis 60000000",
            "transferir ana pedro 100000",
            "saldo ana",
            "saldo luis",
        };
        for (String orden : ordenes) {
            procesar(orden);
        }
    }

    /** Lectura de la orden, reglas, saldos, registro y respuesta: en ese orden y sin separación. */
    static void procesar(String orden) throws IOException {
        String[] p = orden.split(" ");
        if (p[0].equals("saldo")) {
            System.out.println("SALDO " + p[1] + " = " + saldos.get(p[1]));
            return;
        }
        if (!LocalDate.now().equals(dia)) {
            dia = LocalDate.now();
            transferidoHoy.clear();
        }
        String origen = p[1];
        String destino = p[2];
        long valor = Long.parseLong(p[3]);
        if (origen.equals(destino)) {
            System.out.println("ERROR misma cuenta");
            return;
        }
        if (!saldos.containsKey(origen) || !saldos.containsKey(destino)) {
            System.out.println("ERROR cuenta inexistente");
            return;
        }
        if (saldos.get(origen) < valor) {
            System.out.println("ERROR saldo insuficiente");
            return;
        }
        if (transferidoHoy.getOrDefault(origen, 0L) + valor > 80_000_000L) {
            System.out.println("ERROR supera el tope diario");
            return;
        }
        saldos.put(origen, saldos.get(origen) - valor);                      // 1. sale el dinero
        Files.writeString(Path.of(System.getProperty("java.io.tmpdir"), "transferencias.log"),
                LocalDate.now() + " " + origen + " -> " + destino + " " + valor + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);      // 2. se registra (puede fallar)
        saldos.put(destino, saldos.get(destino) + valor);                    // 3. entra el dinero
        transferidoHoy.merge(origen, valor, Long::sum);
        System.out.println("OK " + origen + " -> " + destino + " " + valor);
    }
}
