package banco.adaptadores.entrada.consola;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.CuentaNoEncontrada;
import banco.aplicacion.VistaDeCuenta;
import banco.aplicacion.puertos.entrada.AbrirCuenta;
import banco.aplicacion.puertos.entrada.ConsultarCuenta;
import banco.aplicacion.puertos.entrada.Depositar;
import banco.aplicacion.puertos.entrada.TransferirDinero;
import banco.aplicacion.puertos.entrada.TransferirDinero.Orden;
import banco.aplicacion.puertos.entrada.TransferirDinero.Resultado;
import banco.dominio.Movimiento;
import banco.dominio.ReglaDeNegocioViolada;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Locale;
import java.util.stream.Collectors;

/** Traduce líneas de texto a llamadas a los puertos de entrada, y sus resultados a texto. Habla en pesos. */
public final class Consola {

    private static final String USO = "uso: abrir <titular> <pesos> | depositar <cuenta> <pesos> | "
            + "transferir <clave> <origen> <destino> <pesos> | saldo <cuenta> | extracto <cuenta>";

    private final AbrirCuenta abrirCuenta;
    private final Depositar depositar;
    private final TransferirDinero transferirDinero;
    private final ConsultarCuenta consultarCuenta;
    private final PrintStream salida;

    public Consola(AbrirCuenta abrirCuenta, Depositar depositar, TransferirDinero transferirDinero,
                   ConsultarCuenta consultarCuenta, PrintStream salida) {
        this.abrirCuenta = abrirCuenta;
        this.depositar = depositar;
        this.transferirDinero = transferirDinero;
        this.consultarCuenta = consultarCuenta;
        this.salida = salida;
    }

    public void atender(BufferedReader entrada) throws IOException {
        String linea;
        while ((linea = entrada.readLine()) != null) {
            if (!linea.isBlank()) {
                salida.println(ejecutar(linea.trim()));
            }
        }
    }

    private String ejecutar(String linea) {
        String[] partes = linea.split("\\s+");
        try {
            return switch (partes[0]) {
                case "abrir" -> "abierta " + describir(abrirCuenta.abrir(partes[1], centavos(partes[2])));
                case "depositar" -> describir(depositar.depositar(partes[1], centavos(partes[2])));
                case "transferir" -> {
                    Resultado resultado = transferirDinero.transferir(new Orden(partes[1], partes[2], partes[3], centavos(partes[4])));
                    yield resultado == Resultado.REALIZADA ? "transferencia realizada" : "esa orden ya se había atendido";
                }
                case "saldo" -> consultarCuenta.porId(partes[1]).map(Consola::describir).orElse("no existe la cuenta " + partes[1]);
                case "extracto" -> consultarCuenta.movimientosDe(partes[1]).stream().map(Consola::describir)
                        .collect(Collectors.joining(System.lineSeparator()));
                default -> "no entiendo: " + linea;
            };
        } catch (ReglaDeNegocioViolada | CuentaNoEncontrada | ConflictoDeConcurrencia rechazo) {
            return "rechazada: " + rechazo.getMessage();                 // el núcleo dijo que no
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException malFormada) {
            return USO;                                                  // la línea no tenía la forma esperada
        }
    }

    private static long centavos(String pesos) {
        return Math.multiplyExact(Long.parseLong(pesos), 100L);
    }

    private static String describir(VistaDeCuenta cuenta) {
        return cuenta.id() + " " + cuenta.titular() + ": " + pesos(cuenta.saldoCentavos());
    }

    private static String describir(Movimiento movimiento) {
        return movimiento.fecha() + " " + movimiento.tipo() + " " + pesos(movimiento.valor().centavos()) + " (" + movimiento.referencia() + ")";
    }

    private static String pesos(long centavos) {
        String enteros = String.format(Locale.ROOT, "%,d", centavos / 100).replace(',', '.');
        return "$ " + enteros + String.format(Locale.ROOT, ",%02d", centavos % 100);
    }
}
