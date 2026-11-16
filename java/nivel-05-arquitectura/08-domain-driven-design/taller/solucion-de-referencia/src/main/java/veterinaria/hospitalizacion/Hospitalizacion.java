package veterinaria.hospitalizacion;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Raíz del agregado. Invariantes:
 * 1. Una dosis prescrita no supera la dosis máxima por kilo del medicamento para el peso registrado.
 * 2. No se administra antes de tiempo (se tolera un adelanto de 30 minutos) ni una prescripción suspendida.
 * 3. No se prescribe ni administra después del alta.
 * 4. No se da el alta con tomas atrasadas más de 2 horas sin registrar.
 */
public class Hospitalizacion {

    static final Duration ADELANTO_TOLERADO = Duration.ofMinutes(30);
    static final Duration ATRASO_QUE_BLOQUEA_ALTA = Duration.ofHours(2);

    private final HospitalizacionId id;
    private final PacienteId paciente;                // referencia a otro agregado por identificador
    private final Peso peso;
    private final List<Prescripcion> prescripciones = new ArrayList<>();
    private final List<EventoDeDominio> eventos = new ArrayList<>();
    private boolean dadoDeAlta;

    private Hospitalizacion(HospitalizacionId id, PacienteId paciente, Peso peso) {
        this.id = id;
        this.paciente = paciente;
        this.peso = peso;
    }

    public static Hospitalizacion ingresar(HospitalizacionId id, PacienteId paciente, Peso peso, LocalDateTime cuando) {
        Hospitalizacion h = new Hospitalizacion(id, paciente, peso);
        h.eventos.add(new EventoDeDominio.PacienteHospitalizado(id, paciente, cuando));
        return h;
    }

    public int prescribir(Medicamento medicamento, Dosis dosis, Duration cadaCuanto, LocalDateTime primeraToma) {
        exigirHospitalizado();
        Dosis maxima = Dosis.porPeso(medicamento.maximoMgPorKg(), peso);
        if (dosis.superaA(maxima)) {
            throw new ReglaClinicaIncumplida("La dosis supera el máximo de " + medicamento.nombre() + " para " + peso.gramos() + " g");
        }
        int numero = prescripciones.size() + 1;
        prescripciones.add(new Prescripcion(numero, medicamento.nombre(), dosis, cadaCuanto, primeraToma));
        return numero;
    }

    public void administrar(int numeroPrescripcion, String enfermero, LocalDateTime cuando) {
        exigirHospitalizado();
        Prescripcion p = prescripcion(numeroPrescripcion);
        if (p.suspendida()) throw new ReglaClinicaIncumplida("La prescripción está suspendida");
        if (cuando.isBefore(p.proximaToma().minus(ADELANTO_TOLERADO))) {
            throw new ReglaClinicaIncumplida("Todavía no corresponde la toma; próxima: " + p.proximaToma());
        }
        p.registrarAdministracion(cuando);
        eventos.add(new EventoDeDominio.DosisAdministrada(id, p.medicamento(), p.dosis(), enfermero, cuando));
    }

    public void suspender(int numeroPrescripcion) {
        exigirHospitalizado();
        prescripcion(numeroPrescripcion).suspender();
    }

    public EventoDeDominio.AltaOtorgada darDeAlta(LocalDateTime cuando) {
        exigirHospitalizado();
        boolean hayAtrasadas = prescripciones.stream()
                .filter(p -> !p.suspendida())
                .anyMatch(p -> p.proximaToma().plus(ATRASO_QUE_BLOQUEA_ALTA).isBefore(cuando));
        if (hayAtrasadas) throw new ReglaClinicaIncumplida("Hay tomas atrasadas sin registrar");
        dadoDeAlta = true;
        var alta = new EventoDeDominio.AltaOtorgada(id, paciente, cuando);
        eventos.add(alta);
        return alta;
    }

    /** Los eventos se entregan una vez para que la aplicación los publique. */
    public List<EventoDeDominio> retirarEventos() {
        List<EventoDeDominio> copia = List.copyOf(eventos);
        eventos.clear();
        return copia;
    }

    private Prescripcion prescripcion(int numero) {
        return prescripciones.stream().filter(p -> p.numero() == numero).findFirst()
                .orElseThrow(() -> new ReglaClinicaIncumplida("No existe la prescripción " + numero));
    }

    private void exigirHospitalizado() {
        if (dadoDeAlta) throw new ReglaClinicaIncumplida("El paciente ya fue dado de alta");
    }

    public HospitalizacionId id() { return id; }
    public PacienteId paciente() { return paciente; }
    public List<Prescripcion> prescripciones() { return List.copyOf(prescripciones); }
    public boolean dadoDeAlta() { return dadoDeAlta; }
}
