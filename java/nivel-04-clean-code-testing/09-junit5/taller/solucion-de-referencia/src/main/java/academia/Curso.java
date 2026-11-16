package academia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class Curso {

    private final String nombre;
    private final int cupoMaximo;
    private final List<String> estudiantes = new ArrayList<>();

    Curso(String nombre, int cupoMaximo) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El curso necesita nombre");
        }
        if (cupoMaximo <= 0) {
            throw new IllegalArgumentException("El cupo debe ser mayor que cero");
        }
        this.nombre = nombre;
        this.cupoMaximo = cupoMaximo;
    }

    void inscribir(String estudiante) {
        if (estudiantes.contains(estudiante)) {
            throw new IllegalStateException(estudiante + " ya está inscrito");
        }
        if (cuposLibres() == 0) {
            throw new IllegalStateException("El curso está lleno");
        }
        estudiantes.add(estudiante);
    }

    int cuposLibres() {
        return cupoMaximo - estudiantes.size();
    }

    String nombre() {
        return nombre;
    }

    List<String> estudiantes() {
        return Collections.unmodifiableList(estudiantes);
    }
}
