import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {

    public static void main(String[] args) {
        List<Persona> personas = leerPersonas(List.of("ana;34;CO", "luis;17;CO", ";40;MX", "eva;x;CO"));
        System.out.println(reporte(soloAdultos(personas)));
    }

    static List<Persona> leerPersonas(List<String> lineas) {
        List<Persona> personas = new ArrayList<>();
        for (String linea : lineas) {
            Persona.desde(linea).ifPresent(personas::add);
        }
        return personas;
    }

    static List<Persona> soloAdultos(List<Persona> personas) {
        return personas.stream().filter(Persona::esAdulta).toList();
    }

    static String reporte(List<Persona> personas) {
        StringBuilder texto = new StringBuilder();
        for (Persona persona : personas) {
            texto.append(persona.comoLinea()).append("\n");
        }
        return texto.append("Total: ").append(personas.size()).toString();
    }
}

record Persona(String nombre, int edad, String pais) {

    private static final int MAYORIA_DE_EDAD = 18;

    static Optional<Persona> desde(String linea) {
        String[] campos = linea.split(";");
        if (campos.length != 3 || campos[0].isBlank() || !esNumero(campos[1])) {
            return Optional.empty();
        }
        return Optional.of(new Persona(campos[0], Integer.parseInt(campos[1]), campos[2]));
    }

    boolean esAdulta() {
        return edad >= MAYORIA_DE_EDAD;
    }

    String comoLinea() {
        return capitalizar(nombre) + " (" + edad + ") " + pais;
    }

    private static boolean esNumero(String texto) {
        return !texto.isEmpty() && texto.chars().allMatch(Character::isDigit);
    }

    private static String capitalizar(String texto) {
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }
}
