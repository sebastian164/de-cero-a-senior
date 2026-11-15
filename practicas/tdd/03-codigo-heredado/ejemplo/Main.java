import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Caracterizar antes de tocar: el código viejo es la referencia del nuevo. */
public class Main {

    // ----- El código heredado, tal cual. No se toca. -----
    static double envioHeredado(double peso, String zona, boolean urgente, String tipoCliente) {
        double r = 0;
        if (zona.equals("LOCAL")) {
            if (peso <= 1) r = 5000; else if (peso <= 5) r = 8000; else r = 8000 + (peso - 5) * 1200;
        } else if (zona.equals("NACIONAL")) {
            if (peso <= 1) r = 9000; else if (peso <= 5) r = 14000; else r = 14000 + (peso - 5) * 2000;
        } else {
            r = 30000 + peso * 4000;
        }
        if (urgente) {
            if (zona.equals("LOCAL")) r = r * 1.5; else r = r * 1.8;
        }
        if (tipoCliente.equals("VIP")) {
            r = r - r * 0.1;
            if (urgente && zona.equals("LOCAL")) r = r - 1000;
        } else if (tipoCliente.equals("EMPLEADO")) {
            r = r / 2;
        }
        if (r < 0) r = 0;
        return Math.round(r);
    }

    // ----- La versión refactorizada: mismas reglas, con nombre -----
    static double envio(double peso, String zona, boolean urgente, String tipoCliente) {
        double base = tarifaBase(peso, zona);
        double conUrgencia = urgente ? base * recargoPorUrgencia(zona) : base;
        double conDescuento = aplicarDescuento(conUrgencia, zona, urgente, tipoCliente);
        return Math.round(Math.max(0, conDescuento));
    }

    static double tarifaBase(double peso, String zona) {
        return switch (zona) {
            case "LOCAL" -> porTramos(peso, 5000, 8000, 1200);
            case "NACIONAL" -> porTramos(peso, 9000, 14000, 2000);
            default -> 30000 + peso * 4000;
        };
    }

    static double porTramos(double peso, double hastaUnKilo, double hastaCincoKilos, double porKiloAdicional) {
        if (peso <= 1) {
            return hastaUnKilo;
        }
        if (peso <= 5) {
            return hastaCincoKilos;
        }
        return hastaCincoKilos + (peso - 5) * porKiloAdicional;
    }

    static double recargoPorUrgencia(String zona) {
        return zona.equals("LOCAL") ? 1.5 : 1.8;
    }

    static double aplicarDescuento(double valor, String zona, boolean urgente, String tipoCliente) {
        return switch (tipoCliente) {
            // La rebaja extra de 1000 al VIP urgente local es rara. Se conserva: no sabemos si alguien la pidió.
            case "VIP" -> valor - valor * 0.1 - (urgente && zona.equals("LOCAL") ? 1000 : 0);
            case "EMPLEADO" -> valor / 2;
            default -> valor;
        };
    }

    public static void main(String[] args) {
        List<Double> pesos = List.of(0.5, 1.0, 1.01, 3.0, 5.0, 5.01, 10.0, 25.0);      // a cada lado de cada límite
        List<String> zonas = List.of("LOCAL", "NACIONAL", "INTERNACIONAL");
        List<Boolean> urgencias = List.of(false, true);
        List<String> clientes = List.of("NORMAL", "VIP", "EMPLEADO");

        // 1. La muestra de referencia: lo que el código heredado hace HOY
        Map<String, Double> referencia = new LinkedHashMap<>();
        for (double peso : pesos) {
            for (String zona : zonas) {
                for (boolean urgente : urgencias) {
                    for (String cliente : clientes) {
                        String caso = peso + " kg, " + zona + (urgente ? ", urgente, " : ", normal, ") + cliente;
                        referencia.put(caso, envioHeredado(peso, zona, urgente, cliente));
                    }
                }
            }
        }

        // 2. La versión nueva tiene que dar exactamente lo mismo en todos los casos
        int diferencias = 0;
        for (double peso : pesos) {
            for (String zona : zonas) {
                for (boolean urgente : urgencias) {
                    for (String cliente : clientes) {
                        String caso = peso + " kg, " + zona + (urgente ? ", urgente, " : ", normal, ") + cliente;
                        double esperado = referencia.get(caso);
                        double obtenido = envio(peso, zona, urgente, cliente);
                        if (esperado != obtenido) {
                            diferencias++;
                            System.out.println("DIFERENCIA en " + caso + ": antes " + esperado + ", ahora " + obtenido);
                        }
                    }
                }
            }
        }

        System.out.println(referencia.size() + " combinaciones comparadas, " + diferencias + " diferencias.");
        System.out.println("Ejemplo: 3.0 kg, LOCAL, urgente, VIP = " + referencia.get("3.0 kg, LOCAL, urgente, VIP"));
        if (diferencias > 0) {
            System.exit(1);
        }
    }
}
