package banco.transferencias.adaptador.entrada.cli;

import banco.transferencias.aplicacion.puerto.entrada.TransferirDinero;
import banco.transferencias.dominio.Transferencia;

import java.io.PrintStream;

/** Adaptador primario de línea de comandos: transferir <origen> <destino> <centavos>. */
public class TransferenciaCli {
    private final TransferirDinero transferir;

    public TransferenciaCli(TransferirDinero transferir) { this.transferir = transferir; }

    public int ejecutar(String[] args, PrintStream salida) {
        if (args.length != 3) {
            salida.println("uso: transferir <origen> <destino> <centavos>");
            return 2;
        }
        Transferencia t;
        try {
            t = transferir.transferir(new TransferirDinero.Comando(args[0], args[1], Long.parseLong(args[2])));
        } catch (IllegalArgumentException e) {      // incluye NumberFormatException y el número de cuenta inválido
            salida.println("ERROR: " + e.getMessage());
            return 2;
        }
        salida.println(t.aceptada() ? "OK " + t.id() : "RECHAZADA: " + t.motivoRechazo());
        return t.aceptada() ? 0 : 1;
    }
}
