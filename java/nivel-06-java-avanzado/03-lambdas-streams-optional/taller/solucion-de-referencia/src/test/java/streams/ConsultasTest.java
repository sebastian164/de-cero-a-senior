package streams;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static streams.Transaccion.Tipo.COMPRA;
import static streams.Transaccion.Tipo.DEVOLUCION;
import static streams.Transaccion.Tipo.TRANSFERENCIA;

class ConsultasTest {

    static final LocalDate HOY = LocalDate.of(2030, 3, 31);

    static final List<Transaccion> DATOS = List.of(
            new Transaccion("ana", HOY, COMPRA, 100, "mercado"),
            new Transaccion("ana", HOY.minusDays(3), COMPRA, 300, "tecnologia"),
            new Transaccion("luis", HOY.minusDays(1), TRANSFERENCIA, 50, "servicios"),
            new Transaccion("luis", HOY.minusDays(2), COMPRA, 200, "mercado"),
            new Transaccion("luis", HOY.minusDays(40), COMPRA, 900, "tecnologia"),   // fuera del último mes
            new Transaccion("eva", HOY.minusDays(10), DEVOLUCION, 80, "mercado"),
            new Transaccion("eva", HOY.minusDays(11), COMPRA, 20, "servicios"));

    @Test void totalPorCategoriaOrdenado() {
        assertThat(Consultas.totalPorCategoria(DATOS)).containsExactly(
                Map.entry("tecnologia", 1200L), Map.entry("mercado", 380L), Map.entry("servicios", 70L));
    }

    @Test void conBuclesDaLoMismo() {
        assertThat(Consultas.totalPorCategoriaConBucles(DATOS)).containsExactlyEntriesOf(Consultas.totalPorCategoria(DATOS));
    }

    @Test void clienteMasActivoDelUltimoMes() {
        // ana 2, luis 2 (la de hace 40 días no cuenta), eva 2: empate, gana el primero alfabético
        assertThat(Consultas.clienteMasActivo(DATOS, HOY)).contains("ana");
        var conUnaMas = new java.util.ArrayList<>(DATOS);
        conUnaMas.add(new Transaccion("luis", HOY, COMPRA, 1, "mercado"));
        assertThat(Consultas.clienteMasActivo(conUnaMas, HOY)).contains("luis");
        assertThat(Consultas.clienteMasActivo(List.of(), HOY)).isEmpty();
    }

    @Test void promedioPorTipo() {
        assertThat(Consultas.promedioPorTipo(DATOS)).containsExactlyInAnyOrderEntriesOf(Map.of(
                COMPRA, (100 + 300 + 200 + 900 + 20) / 5.0, TRANSFERENCIA, 50.0, DEVOLUCION, 80.0));
    }

    @Test void particion() {
        assertThat(Consultas.particionPorUmbral(DATOS, 100)).containsExactlyInAnyOrderEntriesOf(Map.of(true, 3L, false, 4L));
    }

    @Test void parallelStreamConEstadoCompartidoPierdeActualizaciones() {
        int n = 5_000_000;
        boolean seObservoElError = false;
        for (int intento = 0; intento < 20 && !seObservoElError; intento++) {
            seObservoElError = Consultas.sumaConEstadoCompartido(n) != n;
        }
        assertThat(seObservoElError).as("la suma con estado compartido debe fallar al menos una vez").isTrue();
        assertThat(Consultas.sumaCorrecta(n)).isEqualTo(n);
    }

    @Test void optionalEnLugarDeNullsAnidados() {
        var completo = new Consultas.Cliente("ana", new Consultas.Direccion(new Consultas.Ciudad("Cali", new Consultas.Pais("Colombia"))));
        var sinPais = new Consultas.Cliente("ana", new Consultas.Direccion(new Consultas.Ciudad("Cali", null)));
        var sinDireccion = new Consultas.Cliente("ana", null);
        for (var c : java.util.Arrays.asList(completo, sinPais, sinDireccion, null)) {
            assertThat(Consultas.paisConOptional(c)).isEqualTo(Consultas.paisConNulls(c));
        }
        assertThat(Consultas.paisConOptional(completo)).isEqualTo("Colombia");
        assertThat(Consultas.paisConOptional(sinPais)).isEqualTo("Desconocido");
    }
}
