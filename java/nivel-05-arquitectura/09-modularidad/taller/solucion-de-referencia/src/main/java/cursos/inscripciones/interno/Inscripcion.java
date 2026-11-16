package cursos.inscripciones.interno;

public class Inscripcion {
    public enum Estado { ACTIVA, FINALIZADA }

    private final String estudiante;
    private final String cursoId;
    private Estado estado = Estado.ACTIVA;

    public Inscripcion(String estudiante, String cursoId) { this.estudiante = estudiante; this.cursoId = cursoId; }

    public void finalizar() {
        if (estado != Estado.ACTIVA) throw new IllegalStateException("La inscripción no está activa");
        estado = Estado.FINALIZADA;
    }

    public String estudiante() { return estudiante; }
    public String cursoId() { return cursoId; }
    public Estado estado() { return estado; }
}
