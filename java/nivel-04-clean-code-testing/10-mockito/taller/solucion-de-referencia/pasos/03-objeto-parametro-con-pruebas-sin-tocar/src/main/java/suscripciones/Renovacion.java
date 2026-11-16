package suscripciones;

import java.time.LocalDate;

/** Objeto parámetro: lo que antes viajaba como cuatro argumentos sueltos. */
public record Renovacion(LocalDate fecha, String suscriptor, long valorEnPesos, LocalDate vence, Comprobante comprobante) {
}
