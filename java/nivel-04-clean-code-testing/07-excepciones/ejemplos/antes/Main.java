import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        Cuentas cuentas = new Cuentas();
        System.out.println(cuentas.transferir("A", "B", 500_000));
        System.out.println(cuentas.transferir("A", "X", 10));
        System.out.println(cuentas.saldos);
    }
}

class Cuentas {

    final Map<String, Long> saldos = new HashMap<>(Map.of("A", 100_000L, "B", 0L));

    /** Devuelve 0 si todo va bien, -1 si hay error. */
    int transferir(String origen, String destino, long monto) {
        try {
            long saldoOrigen = saldos.get(origen);
            if (saldoOrigen < monto) {
                return -1;
            }
            saldos.put(origen, saldoOrigen - monto);
            long saldoDestino = saldos.get(destino);   // NullPointerException si no existe
            saldos.put(destino, saldoDestino + monto);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }
}
