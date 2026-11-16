/** Cliente: módulo de marketing. */
public interface CampanasDeMarketing {
    String exportarCsv();
    void enviarCorreoMasivo(String asunto, String cuerpo);
}
