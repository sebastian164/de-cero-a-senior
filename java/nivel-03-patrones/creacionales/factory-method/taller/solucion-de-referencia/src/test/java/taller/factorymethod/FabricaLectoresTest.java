package taller.factorymethod;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FabricaLectoresTest {

    private final FabricaLectores fabrica = FabricaLectores.conFormatosEstandar();

    @Test
    void eligeLectorPorExtension() {
        assertInstanceOf(LectorCsv.class, fabrica.paraArchivo("a.csv"));
        assertInstanceOf(LectorXml.class, fabrica.paraArchivo("a.XML"));
        assertInstanceOf(LectorAnchoFijo.class, fabrica.paraArchivo("a.txt"));
        assertInstanceOf(LectorJson.class, fabrica.paraArchivo("a.json"));
    }

    @Test
    void extensionDesconocidaFallaConMensajeClaro() {
        var e = assertThrows(FormatoNoSoportadoException.class, () -> fabrica.paraArchivo("a.pdf"));
        assertTrue(e.getMessage().contains("[csv, json, txt, xml]"), e.getMessage());
        assertThrows(FormatoNoSoportadoException.class, () -> fabrica.paraArchivo("sin-extension"));
    }

    @Test
    void todosLosLectoresProducenMovimientos() {
        assertEquals(List.of(new Movimiento(LocalDate.of(2025, 5, 1), "Nómina", 3_500_000),
                        new Movimiento(LocalDate.of(2025, 5, 3), "Arriendo", -1_200_000)),
                fabrica.paraArchivo("x.csv").leer("2025-05-01;Nómina;3500000\n2025-05-03;Arriendo;-1200000"));
        assertEquals(List.of(new Movimiento(LocalDate.of(2025, 5, 2), "Mercado", -250_000)),
                fabrica.paraArchivo("x.xml").leer("<extracto><mov fecha=\"2025-05-02\" desc=\"Mercado\" valor=\"-250000\"/></extracto>"));
        assertEquals(List.of(new Movimiento(LocalDate.of(2025, 5, 4), "Transferencia recibida", 150_000),
                        new Movimiento(LocalDate.of(2025, 5, 6), "Retiro cajero", -50_000)),
                fabrica.paraArchivo("x.txt").leer(String.format("%s%-22s%s%n%s%-22s%s", "20250504", "Transferencia recibida", "+0000150000", "20250506", "Retiro cajero", "-0000050000")));
        assertEquals(List.of(new Movimiento(LocalDate.of(2025, 5, 5), "Intereses", 1200)),
                fabrica.paraArchivo("x.json").leer("[{\"fecha\":\"2025-05-05\",\"desc\":\"Intereses\",\"valor\":1200}]"));
    }

    @Test
    void registrarUnFormatoNoModificaLaFabrica() {
        FabricaLectores f = new FabricaLectores().registrar("ofx", () -> contenido -> List.of());
        assertTrue(f.paraArchivo("banco.ofx").leer("x").isEmpty());
    }

    @Test
    void metodosDeFabricaConNombre() {
        LocalDate hoy = LocalDate.of(2025, 1, 1);
        assertEquals(-100, Movimiento.egreso(hoy, "x", 100).valor());
        assertEquals(100, Movimiento.ingreso(hoy, "x", 100).valor());
        assertTrue(Movimiento.ingreso(hoy, "x", 100).esIngreso());
        assertThrows(IllegalArgumentException.class, () -> Movimiento.egreso(hoy, "x", -100));
    }
}
