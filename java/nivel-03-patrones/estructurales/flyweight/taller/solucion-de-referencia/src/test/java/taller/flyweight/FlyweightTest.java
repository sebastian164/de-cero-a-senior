package taller.flyweight;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class FlyweightTest {

    @Test
    void laFabricaComparteInstanciasIguales() {
        FabricaFormatos f = new FabricaFormatos();
        Formato a = f.obtener(new String("Arial"), 11, 0);
        Formato b = f.obtener(new String("Arial"), 11, 0);
        assertSame(a, b);
        assertNotSame(a, f.obtener("Arial", 12, 0));
    }

    @Test
    void doscientosMilCaracteresConDosFormatos() {
        FabricaFormatos f = new FabricaFormatos();
        Caracter[] doc = Documentos.ligero(200_000, f);
        assertEquals(2, f.cantidad());
        assertEquals(2, Arrays.stream(doc).map(c -> c.formato).distinct().count());
    }

    @Test
    void flyweightUsaMuchaMenosMemoria() {
        long pesado = Medidor.bytesRetenidos(() -> Documentos.pesado(200_000));
        long ligero = Medidor.bytesRetenidos(() -> Documentos.ligero(200_000, new FabricaFormatos()));
        assertTrue(ligero < pesado * 0.6, "pesado=" + pesado + " ligero=" + ligero);
    }

    @Test
    void elFormatoCompartidoEsInmutable() {
        for (var campo : Formato.class.getDeclaredFields()) {
            assertTrue(Modifier.isFinal(campo.getModifiers()), campo.getName());
        }
        FabricaFormatos f = new FabricaFormatos();
        Formato negro = f.obtener("Arial", 11, 0);
        Formato rojo = f.obtener(negro.conColor(0xFF0000));
        assertEquals(0, negro.rgb(), "cambiar el color no afecta a los demás caracteres");
        assertEquals(0xFF0000, rojo.rgb());
    }

    @Test
    void enterosEnCaja() {
        assertEquals("127 == 127: true | 128 == 128: false | 128 equals 128: true", EnterosEnCache.explicar());
        assertTrue(EnterosEnCache.mismoCliente(100, 100));
        assertFalse(EnterosEnCache.mismoCliente(5000, 5000), "el error: mismo id, == dice que no");
    }
}
