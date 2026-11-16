package taller.composite;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrganigramaTest {

    Area empresa, tecnologia, desarrollo;

    @BeforeEach
    void armar() {
        empresa = new Area("Empresa");
        tecnologia = new Area("Tecnología");
        desarrollo = new Area("Desarrollo");
        empresa.agregar(new Empleado("Gerente", 12_000_000));
        empresa.agregar(tecnologia);
        tecnologia.agregar(new Empleado("CTO", 15_000_000));
        tecnologia.agregar(desarrollo);
        desarrollo.agregar(new Empleado("Ana", 7_000_000));
        desarrollo.agregar(new Empleado("Luis", 5_500_000));
    }

    @Test
    void costoYPersonasDeCualquierNodo() {
        assertEquals(39_500_000, empresa.costoMensual());
        assertEquals(4, empresa.personas());
        assertEquals(27_500_000, tecnologia.costoMensual());
        assertEquals(12_500_000, desarrollo.costoMensual());
        assertEquals(1, new Empleado("x", 1).personas());
        assertEquals(0, new Area("vacía").personas());
    }

    @Test
    void impideCiclosDirectosEIndirectos() {
        assertThrows(IllegalArgumentException.class, () -> empresa.agregar(empresa));
        assertThrows(IllegalArgumentException.class, () -> desarrollo.agregar(empresa));
        assertThrows(IllegalArgumentException.class, () -> desarrollo.agregar(tecnologia));
        assertEquals(4, empresa.personas(), "el árbol no cambió");
    }

    @Test
    void listarLosQueGananMasDe() {
        assertEquals(List.of("Gerente", "CTO", "Ana"), empresa.queGananMasDe(6_000_000));
        assertEquals(List.of(), desarrollo.queGananMasDe(10_000_000));
    }

    @Test
    void laVersionSealedDaLoMismo() {
        Organigrama.Nodo n = Organigrama.convertir(empresa);
        assertEquals(empresa.costoMensual(), Organigrama.costo(n));
        assertEquals(empresa.personas(), Organigrama.personas(n));
        assertEquals(empresa.queGananMasDe(6_000_000), Organigrama.queGananMasDe(n, 6_000_000));
    }
}
