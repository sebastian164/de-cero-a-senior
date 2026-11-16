package taller.mediator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class TorreTest {

    Torre torre = new Torre();
    Avion a = new Avion("A", torre), b = new Avion("B", torre), c = new Avion("C", torre), d = new Avion("D", torre);

    @Test
    void sinMediadorLasReferenciasCrecenCuadraticamente() {
        assertEquals(6, taller.mediator.sinmediador.Aeropuerto.referencias(3));
        assertEquals(90, taller.mediator.sinmediador.Aeropuerto.referencias(10));
    }

    @Test
    void unSoloAvionEnPistaYLosDemasEsperan() {
        a.solicitar(Operacion.DESPEGAR, false);
        b.solicitar(Operacion.DESPEGAR, false);
        assertEquals("A", torre.enPista().orElseThrow().matricula());
        assertEquals(List.of("Autorizado a despegar"), a.mensajes());
        assertEquals(List.of("En espera, posición 1"), b.mensajes());
        a.liberarPista();
        assertEquals("B", torre.enPista().orElseThrow().matricula());
    }

    @Test
    void emergenciasPrimeroLuegoAterrizajesLuegoOrdenDeLlegada() {
        a.solicitar(Operacion.DESPEGAR, false);      // toma la pista
        b.solicitar(Operacion.DESPEGAR, false);
        c.solicitar(Operacion.ATERRIZAR, false);
        d.solicitar(Operacion.DESPEGAR, true);
        assertEquals(List.of("D", "C", "B"), torre.cola());
        assertEquals("En espera, posición 1", d.mensajes().get(0));
        a.liberarPista();
        assertEquals("D", torre.enPista().orElseThrow().matricula());
    }

    @Test
    void soloQuienTieneLaPistaPuedeLiberarla() {
        a.solicitar(Operacion.ATERRIZAR, false);
        b.solicitar(Operacion.ATERRIZAR, false);
        b.liberarPista();
        assertEquals("A", torre.enPista().orElseThrow().matricula());
        assertEquals("No tiene la pista asignada", b.mensajes().get(1));
    }

    @Test
    void losAvionesNoSeConocenEntreSi() {
        for (var campo : Avion.class.getDeclaredFields()) {
            assertNotEquals(Avion.class, campo.getType());
            assertNotEquals(List.class.getName() + "<Avion>", campo.getGenericType().getTypeName());
        }
    }
}
