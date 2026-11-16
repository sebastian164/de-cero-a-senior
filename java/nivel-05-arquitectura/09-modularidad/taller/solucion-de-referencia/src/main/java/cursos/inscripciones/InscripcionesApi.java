package cursos.inscripciones;

public interface InscripcionesApi {
    void inscribir(String estudiante, String cursoId);
    void completar(String estudiante, String cursoId);
}
