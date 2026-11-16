package suscripciones;

/** Lo que el negocio le pide a quien cobra. Habla de suscriptores y pesos, no de tarjetas ni reintentos. */
public record Cobro(String suscriptor, long valorEnPesos, String concepto) {
}
