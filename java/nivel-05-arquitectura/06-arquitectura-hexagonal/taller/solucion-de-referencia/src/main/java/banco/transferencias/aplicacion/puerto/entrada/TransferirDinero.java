package banco.transferencias.aplicacion.puerto.entrada;

import banco.transferencias.dominio.Transferencia;

/** Puerto de entrada: lo que la aplicación ofrece a cualquier canal. */
public interface TransferirDinero {
    record Comando(String origen, String destino, long centavos) { }

    /** Devuelve el registro; si fue rechazada, el registro lo dice (y también queda guardado). */
    Transferencia transferir(Comando comando);
}
