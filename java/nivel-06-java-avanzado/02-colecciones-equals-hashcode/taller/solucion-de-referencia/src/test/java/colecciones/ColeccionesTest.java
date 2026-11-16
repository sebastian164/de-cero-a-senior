package colecciones;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static colecciones.Pedido.Estado.ENVIADO;
import static colecciones.Pedido.Estado.PAGADO;
import static colecciones.Pedido.Estado.PENDIENTE;
import static org.assertj.core.api.Assertions.assertThat;

class ColeccionesTest {

    @Test
    void medicionDeBusquedas() {
        List<MedirBusquedas.Medicion> m = MedirBusquedas.ejecutar();
        System.out.println(m);
        assertThat(m.get(0).encontrados()).isEqualTo(m.get(1).encontrados());
        assertThat(m.get(0).milisegundos()).isGreaterThan(100 * Math.max(1, m.get(1).milisegundos()));
    }

    @Test
    void equalsSinHashCodePierdeElementosEnUnHashSet() {
        var a = new ClienteSinHashCode("123");
        var b = new ClienteSinHashCode("123");
        assertThat(a).isEqualTo(b);                         // iguales según equals...
        Set<ClienteSinHashCode> conjunto = new HashSet<>();
        conjunto.add(a);
        assertThat(conjunto.contains(b)).isFalse();         // ...pero el conjunto no lo encuentra
        conjunto.add(b);
        assertThat(conjunto).hasSize(2);                    // y admite "duplicados"
    }

    @Test
    void claveMutableYSuCorreccion() {
        List<String> clave = new ArrayList<>(List.of("a"));
        Map<List<String>, String> mapa = new HashMap<>();
        mapa.put(clave, "valor");
        clave.add("b");
        assertThat(mapa.get(clave)).isNull();                         // el valor quedó inalcanzable
        assertThat(mapa.get(List.of("a"))).isNull();                  // ni con la clave original
        assertThat(mapa).hasSize(1);                                  // pero sigue ocupando memoria

        // Corrección: claves inmutables (copia defensiva o un record de campos inmutables)
        Map<List<String>, String> corregido = new HashMap<>();
        List<String> origen = new ArrayList<>(List.of("a"));
        corregido.put(List.copyOf(origen), "valor");
        origen.add("b");
        assertThat(corregido.get(List.of("a"))).isEqualTo("valor");
    }

    @Test
    void comparadorCompuesto() {
        LocalDate d1 = LocalDate.of(2030, 1, 1), d2 = d1.plusDays(1);
        List<Pedido> pedidos = new ArrayList<>(List.of(
                new Pedido("zoe", PAGADO, d1), new Pedido("ana", PENDIENTE, d1),
                new Pedido("luis", PENDIENTE, d2), new Pedido("bea", PENDIENTE, d2),
                new Pedido("eva", ENVIADO, d2)));
        pedidos.sort(Pedido.ORDEN_DE_TRABAJO);
        assertThat(pedidos).extracting(Pedido::cliente).containsExactly("bea", "luis", "ana", "zoe", "eva");
    }
}
