/**
 * Lo que la cuenta necesita saber sobre comisiones: cuánto cobrar.
 * Cómo se calcula es asunto de cada implementación.
 */
public interface PoliticaComision {
    long calcular(long monto);
}
