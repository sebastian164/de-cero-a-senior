import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Curso curso = new Curso();
        curso.setNombre("Arquitectura de software");
        curso.setCupoMaximo(2);

        // La regla del cupo la aplica quien usa el curso... cuando se acuerda.
        if (curso.getEstudiantes().size() < curso.getCupoMaximo()) {
            curso.getEstudiantes().add("Ana");
        }
        curso.getEstudiantes().add("Luis");
        curso.getEstudiantes().add("Marta");
        curso.getEstudiantes().add("Marta");
        curso.setCupoMaximo(-5);

        System.out.println(curso.getNombre() + ": " + curso.getEstudiantes()
                + " cupo " + curso.getCupoMaximo());
    }
}

class Curso {

    private String nombre;
    private int cupoMaximo;
    private List<String> estudiantes = new ArrayList<>();

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public int getCupoMaximo() { return cupoMaximo; }
    public void setCupoMaximo(int cupoMaximo) { this.cupoMaximo = cupoMaximo; }

    public List<String> getEstudiantes() { return estudiantes; }
    public void setEstudiantes(List<String> estudiantes) { this.estudiantes = estudiantes; }
}
