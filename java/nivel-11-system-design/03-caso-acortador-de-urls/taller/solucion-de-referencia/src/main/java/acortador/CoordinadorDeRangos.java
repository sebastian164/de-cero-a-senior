package acortador;

/** Reparte bloques de números: cada llamada entrega el índice de un bloque que nadie más recibirá. */
public interface CoordinadorDeRangos {
    long siguienteBloque();
}
