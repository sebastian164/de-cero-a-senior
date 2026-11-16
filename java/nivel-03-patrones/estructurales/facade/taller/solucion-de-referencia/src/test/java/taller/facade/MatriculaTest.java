package taller.facade;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatriculaTest {

    static final MedioPago APROBADO = monto -> true;
    RegistroAcademico registro;
    Tesoreria tesoreria;
    Correo correo;
    Matricula matricula;

    @BeforeEach
    void armar() {
        registro = new RegistroAcademico();
        tesoreria = new Tesoreria();
        correo = new Correo();
        matricula = new Matricula(registro, tesoreria, new Horarios(), correo);
    }

    @Test
    void inscripcionExitosaCoordinaLosCuatroSubsistemas() {
        ResultadoMatricula r = matricula.inscribir("ana", List.of("CALCULO-2", "FISICA-1"), APROBADO);
        assertTrue(r.exitosa());
        assertEquals("REC-1", r.recibo());
        assertEquals(1, registro.cuposDisponibles("CALCULO-2"));
        assertEquals("Copia: REC-1 ana $1500000", tesoreria.reimprimir("REC-1"));
        assertTrue(tesoreria.pagado("REC-1"), "la fachada confirmó el pago");
        assertEquals(List.of("Para ana: Matrícula registrada en [CALCULO-2, FISICA-1], recibo REC-1"), correo.enviados());
    }

    @Test
    void cruceDeHorarioLiberaLosCuposReservados() {
        ResultadoMatricula r = matricula.inscribir("luis", List.of("CALCULO-2", "PROGRAMACION"), APROBADO);
        assertFalse(r.exitosa());
        assertEquals(List.of("Cruce: CALCULO-2 y PROGRAMACION (LUN-08)"), r.motivos());
        assertEquals(2, registro.cuposDisponibles("CALCULO-2"));
        assertEquals(25, registro.cuposDisponibles("PROGRAMACION"));
        assertTrue(correo.enviados().isEmpty());
    }

    @Test
    void prerrequisitoFaltanteNoReservaNada() {
        ResultadoMatricula r = matricula.inscribir("marta", List.of("CALCULO-3"), APROBADO);
        assertEquals(List.of("CALCULO-3 requiere CALCULO-2"), r.motivos());
        assertEquals(20, registro.cuposDisponibles("CALCULO-3"));
    }

    @Test
    void sinCupoEsTodoONada() {
        matricula.inscribir("ana", List.of("CALCULO-2"), APROBADO);
        matricula.inscribir("luis", List.of("CALCULO-2"), APROBADO);
        ResultadoMatricula r = matricula.inscribir("ana", List.of("FISICA-1", "CALCULO-2"), APROBADO);
        assertEquals(List.of("Sin cupo en CALCULO-2"), r.motivos());
        assertEquals(30, registro.cuposDisponibles("FISICA-1"));
    }

    @Test
    void pagoRechazadoLiberaLosCupos() {
        ResultadoMatricula r = matricula.inscribir("ana", List.of("CALCULO-2", "FISICA-1"), monto -> false);
        assertFalse(r.exitosa());
        assertEquals(List.of("Pago rechazado para REC-1"), r.motivos());
        assertEquals(2, registro.cuposDisponibles("CALCULO-2"));
        assertEquals(30, registro.cuposDisponibles("FISICA-1"));
        assertFalse(tesoreria.pagado("REC-1"));
        assertEquals("Copia: REC-1 ana $1500000 (ANULADO)", tesoreria.reimprimir("REC-1"));
        assertTrue(correo.enviados().isEmpty(), "sin pago no hay correo de matrícula");
    }

    @Test
    void elMedioRecibeElValorDelRecibo() {
        long[] cobrado = new long[1];
        matricula.inscribir("ana", List.of("FISICA-1"), monto -> { cobrado[0] = monto; return true; });
        assertEquals(750_000, cobrado[0], "3 créditos × $250.000");
    }
}
