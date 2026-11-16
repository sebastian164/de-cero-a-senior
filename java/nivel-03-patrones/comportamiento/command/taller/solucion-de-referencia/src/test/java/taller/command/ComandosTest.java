package taller.command;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ComandosTest {

    Banco banco;
    Historial h;

    @BeforeEach
    void armar() {
        banco = new Banco();
        banco.abrir("A", 100);
        banco.abrir("B", 0);
        h = new Historial();
    }

    @Test
    void cadaOperacionSeDeshace() {
        h.ejecutar(new Depositar(banco, "A", 50));
        h.ejecutar(new Retirar(banco, "A", 30));
        h.ejecutar(new Transferir(banco, "A", "B", 20));
        assertEquals(100, banco.saldo("A"));
        assertEquals(20, banco.saldo("B"));
        h.deshacer();
        assertEquals(120, banco.saldo("A"));
        assertEquals(0, banco.saldo("B"));
        h.deshacer();
        assertEquals(150, banco.saldo("A"));
        h.deshacer();
        assertEquals(100, banco.saldo("A"));
        assertFalse(h.deshacer());
    }

    @Test
    void rehacerYUnComandoNuevoVaciaLaPilaDeRehacer() {
        h.ejecutar(new Depositar(banco, "A", 50));
        h.deshacer();
        assertEquals(1, h.pendientesDeRehacer());
        h.rehacer();
        assertEquals(150, banco.saldo("A"));
        h.deshacer();
        h.ejecutar(new Depositar(banco, "B", 5));
        assertEquals(0, h.pendientesDeRehacer());
        assertFalse(h.rehacer());
        assertEquals(100, banco.saldo("A"));
    }

    @Test
    void macroSeDeshaceCompletoEnOrdenInverso() {
        h.ejecutar(new Macro(List.of(new Retirar(banco, "A", 40), new Depositar(banco, "B", 40), new Transferir(banco, "B", "A", 10))));
        assertEquals(70, banco.saldo("A"));
        assertEquals(30, banco.saldo("B"));
        h.deshacer();
        assertEquals(100, banco.saldo("A"));
        assertEquals(0, banco.saldo("B"));
    }

    @Test
    void falloEnMitadDelMacroRevierteLoAnteriorYNoEntraAlHistorial() {
        Comando macro = new Macro(List.of(new Depositar(banco, "B", 10), new Transferir(banco, "A", "B", 30),
                new Retirar(banco, "A", 1_000)));
        assertThrows(FondosInsuficientesException.class, () -> h.ejecutar(macro));
        assertEquals(100, banco.saldo("A"));
        assertEquals(0, banco.saldo("B"));
        assertFalse(h.deshacer(), "el macro fallido no quedó en el historial");
    }

    @Test
    void comandoFrenteAEvento() {
        assertEquals("Comando ConfirmarPedido[pedidoId=P-1, solicitadoPor=ana] -> eventos [PedidoConfirmado[pedidoId=P-1, cuando=1970-01-01T00:00:00Z]]",
                ComandoVsEvento.ejemplo());
    }
}
