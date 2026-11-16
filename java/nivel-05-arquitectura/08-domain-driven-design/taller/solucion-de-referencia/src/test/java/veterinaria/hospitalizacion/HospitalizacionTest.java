package veterinaria.hospitalizacion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HospitalizacionTest {

    static final LocalDateTime INGRESO = LocalDateTime.of(2030, 4, 1, 8, 0);
    static final Medicamento MELOXICAM = new Medicamento("meloxicam", 0.2);   // 0,2 mg/kg máximo

    Hospitalizacion h;

    @BeforeEach
    void ingresar() {
        h = Hospitalizacion.ingresar(new HospitalizacionId("H-1"), new PacienteId("firulais"), Peso.kilos(10), INGRESO);
    }

    @Test
    void rechazaDosisPorEncimaDelMaximoPorPeso() {
        h.prescribir(MELOXICAM, Dosis.miligramos(2.0), Duration.ofHours(24), INGRESO);      // 0,2 * 10 kg = 2 mg: límite
        assertThatThrownBy(() -> h.prescribir(MELOXICAM, Dosis.miligramos(2.001), Duration.ofHours(24), INGRESO))
                .isInstanceOf(ReglaClinicaIncumplida.class).hasMessageContaining("supera el máximo");
    }

    @Test
    void noSeAdministraAntesDeTiempo() {
        int n = h.prescribir(MELOXICAM, Dosis.miligramos(1), Duration.ofHours(12), INGRESO);
        h.administrar(n, "eva", INGRESO);
        h.administrar(n, "eva", INGRESO.plusHours(11).plusMinutes(30));                   // 30 min antes: tolerado
        assertThatThrownBy(() -> h.administrar(n, "eva", INGRESO.plusHours(11).plusMinutes(31).plusHours(11)))
                .hasMessageContaining("Todavía no corresponde");
    }

    @Test
    void noSeAdministraUnaSuspendida() {
        int n = h.prescribir(MELOXICAM, Dosis.miligramos(1), Duration.ofHours(12), INGRESO);
        h.suspender(n);
        assertThatThrownBy(() -> h.administrar(n, "eva", INGRESO)).hasMessageContaining("suspendida");
    }

    @Test
    void elAltaSeBloqueaConTomasAtrasadasYEmiteEvento() {
        int n = h.prescribir(MELOXICAM, Dosis.miligramos(1), Duration.ofHours(12), INGRESO);
        assertThatThrownBy(() -> h.darDeAlta(INGRESO.plusHours(3))).hasMessageContaining("atrasadas");
        h.administrar(n, "eva", INGRESO.plusHours(3));
        var alta = h.darDeAlta(INGRESO.plusHours(4));
        assertThat(alta.paciente()).isEqualTo(new PacienteId("firulais"));
        assertThatThrownBy(() -> h.administrar(n, "eva", INGRESO.plusHours(20))).hasMessageContaining("dado de alta");
    }

    @Test
    void losEventosCuentanLaHistoriaYSeRetiranUnaVez() {
        int n = h.prescribir(MELOXICAM, Dosis.miligramos(1), Duration.ofHours(12), INGRESO);
        h.administrar(n, "eva", INGRESO);
        h.darDeAlta(INGRESO.plusHours(1));
        assertThat(h.retirarEventos()).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("PacienteHospitalizado", "DosisAdministrada", "AltaOtorgada");
        assertThat(h.retirarEventos()).isEmpty();
    }

    @Test
    void lasPrescripcionesNoSeModificanDesdeAfuera() {
        h.prescribir(MELOXICAM, Dosis.miligramos(1), Duration.ofHours(12), INGRESO);
        assertThatThrownBy(() -> h.prescripciones().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
