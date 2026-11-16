package taller.facade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {

    public static void main(String[] args) {
        RegistroAcademico registro = new RegistroAcademico();
        Tesoreria tesoreria = new Tesoreria();
        Correo correo = new Correo();
        Matricula matricula = new Matricula(registro, tesoreria, new Horarios(), correo);

        MedioPago aprobado = monto -> true;
        System.out.println(matricula.inscribir("ana", List.of("CALCULO-2", "FISICA-1"), aprobado));
        System.out.println("Cupos CALCULO-2: " + registro.cuposDisponibles("CALCULO-2") + "; REC-1 pagado: " + tesoreria.pagado("REC-1"));
        System.out.println(matricula.inscribir("luis", List.of("CALCULO-2", "PROGRAMACION"), aprobado));   // cruce
        System.out.println("Cupos CALCULO-2 tras el cruce: " + registro.cuposDisponibles("CALCULO-2"));
        System.out.println(matricula.inscribir("marta", List.of("CALCULO-3"), aprobado));                 // prerrequisito
        System.out.println(matricula.inscribir("luis", List.of("FISICA-1"), monto -> monto <= 500_000));   // pago rechazado
        System.out.println("Cupos FISICA-1 tras el rechazo: " + registro.cuposDisponibles("FISICA-1"));
        // Paso 4: operación fina que no está en la fachada: el cliente usa Tesoreria directamente.
        System.out.println(tesoreria.reimprimir("REC-1"));
        correo.enviados().forEach(System.out::println);
    }
}

// ===================== Subsistemas =====================

final class RegistroAcademico {
    private final Map<String, Integer> cupos = new HashMap<>(Map.of(
            "CALCULO-2", 2, "FISICA-1", 30, "PROGRAMACION", 25, "CALCULO-3", 20));
    private final Map<String, String> prerrequisitos = Map.of("CALCULO-3", "CALCULO-2", "CALCULO-2", "CALCULO-1");
    private final Map<String, Set<String>> aprobadas = Map.of(
            "ana", Set.of("CALCULO-1"), "luis", Set.of("CALCULO-1"), "marta", Set.of());

    List<String> prerrequisitosFaltantes(String estudiante, List<String> asignaturas) {
        Set<String> suyas = aprobadas.getOrDefault(estudiante, Set.of());
        List<String> faltan = new ArrayList<>();
        for (String a : asignaturas) {
            String pre = prerrequisitos.get(a);
            if (pre != null && !suyas.contains(pre)) faltan.add(a + " requiere " + pre);
        }
        return faltan;
    }

    /** Reserva todos o ninguno. */
    void reservarCupos(List<String> asignaturas) {
        for (String a : asignaturas) {
            if (cupos.getOrDefault(a, 0) <= 0) throw new IllegalStateException("Sin cupo en " + a);
        }
        asignaturas.forEach(a -> cupos.merge(a, -1, Integer::sum));
    }

    void liberarCupos(List<String> asignaturas) { asignaturas.forEach(a -> cupos.merge(a, 1, Integer::sum)); }
    int cuposDisponibles(String asignatura) { return cupos.get(asignatura); }
}

final class Horarios {
    private final Map<String, String> franja = Map.of(
            "CALCULO-2", "LUN-08", "FISICA-1", "MAR-10", "PROGRAMACION", "LUN-08", "CALCULO-3", "MIE-14");

    List<String> cruces(List<String> asignaturas) {
        Map<String, String> ocupado = new HashMap<>();
        List<String> cruces = new ArrayList<>();
        for (String a : asignaturas) {
            String otra = ocupado.putIfAbsent(franja.get(a), a);
            if (otra != null) cruces.add(otra + " y " + a + " (" + franja.get(a) + ")");
        }
        return cruces;
    }
}

final class Tesoreria {
    private final Map<String, String> recibos = new HashMap<>();
    private final Map<String, Long> valores = new HashMap<>();
    private final Set<String> pagados = new HashSet<>();
    private int consecutivo;
    static final long VALOR_CREDITO = 250_000;

    String generarRecibo(String estudiante, int creditos) {
        String numero = "REC-" + (++consecutivo);
        valores.put(numero, creditos * VALOR_CREDITO);
        recibos.put(numero, numero + " " + estudiante + " $" + creditos * VALOR_CREDITO);
        return numero;
    }
    /** Cobra el valor del recibo con el medio dado; si se aprueba, lo marca pagado. Si no, anula el recibo. */
    boolean confirmarPago(String recibo, MedioPago medio) {
        if (medio.autorizar(valores.get(recibo))) { pagados.add(recibo); return true; }
        recibos.put(recibo, recibos.get(recibo) + " (ANULADO)");
        return false;
    }
    boolean pagado(String recibo) { return pagados.contains(recibo); }
    String reimprimir(String recibo) { return "Copia: " + recibos.get(recibo); }
}

/** La pasarela o el medio con que paga el estudiante (simulado). */
interface MedioPago { boolean autorizar(long monto); }

final class Correo {
    private final List<String> enviados = new ArrayList<>();
    void enviar(String a, String texto) { enviados.add("Para " + a + ": " + texto); }
    List<String> enviados() { return List.copyOf(enviados); }
}

// ===================== Fachada =====================

record ResultadoMatricula(boolean exitosa, String recibo, List<String> motivos) {
    static ResultadoMatricula rechazada(List<String> motivos) { return new ResultadoMatricula(false, null, motivos); }
}

final class Matricula {
    private static final int CREDITOS_POR_ASIGNATURA = 3;   // regla de negocio: ver ANALISIS punto 5

    private final RegistroAcademico registro;
    private final Tesoreria tesoreria;
    private final Horarios horarios;
    private final Correo correo;

    Matricula(RegistroAcademico registro, Tesoreria tesoreria, Horarios horarios, Correo correo) {
        this.registro = registro; this.tesoreria = tesoreria; this.horarios = horarios; this.correo = correo;
    }

    ResultadoMatricula inscribir(String estudiante, List<String> asignaturas, MedioPago pago) {
        List<String> faltan = registro.prerrequisitosFaltantes(estudiante, asignaturas);
        if (!faltan.isEmpty()) return ResultadoMatricula.rechazada(faltan);

        try {
            registro.reservarCupos(asignaturas);
        } catch (IllegalStateException e) {
            return ResultadoMatricula.rechazada(List.of(e.getMessage()));
        }

        List<String> cruces = horarios.cruces(asignaturas);
        if (!cruces.isEmpty()) {
            registro.liberarCupos(asignaturas);           // Paso 3: compensación; deja todo como estaba
            return ResultadoMatricula.rechazada(cruces.stream().map(c -> "Cruce: " + c).toList());
        }

        String recibo = tesoreria.generarRecibo(estudiante, asignaturas.size() * CREDITOS_POR_ASIGNATURA);
        if (!tesoreria.confirmarPago(recibo, pago)) {
            registro.liberarCupos(asignaturas);           // misma compensación: sin pago no hay cupos tomados
            return ResultadoMatricula.rechazada(List.of("Pago rechazado para " + recibo));
        }
        correo.enviar(estudiante, "Matrícula registrada en " + asignaturas + ", recibo " + recibo);
        return new ResultadoMatricula(true, recibo, List.of());
    }
}
