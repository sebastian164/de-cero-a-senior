package transacciones;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.UnexpectedRollbackException;
import transacciones.auditoria.AspectoDeAuditoria;
import transacciones.auditoria.Cuentas;
import transacciones.auditoria.UsuarioActual;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TransaccionesTest {

    @Autowired ServicioPedidos servicio;
    @Autowired ImportadorCorregido corregido;
    @Autowired ExcepcionesComprobadas comprobadas;
    @Autowired Reversion reversion;
    @Autowired Pedidos pedidos;
    @Autowired Cuentas cuentas;
    @Autowired AspectoDeAuditoria auditoria;

    static final List<PedidoDto> LOTE = List.of(new PedidoDto("ana", 10), new PedidoDto("luis", -1), new PedidoDto("eva", 5));

    @BeforeEach void limpiar() { pedidos.vaciar(); }

    // 1
    @Test
    void autoinvocacionNoRevierte() {
        assertThatThrownBy(() -> servicio.importar(LOTE)).hasMessageContaining("luis");
        // ana: 2 filas; luis: su cabecera Y su fila inválida quedaron (no hubo transacción); eva no llegó
        assertThat(pedidos.contar()).isEqualTo(4);
    }

    @Test
    void correccionConOtroBean() {
        assertThatThrownBy(() -> corregido.importarConOtroBean(LOTE)).hasMessageContaining("luis");
        assertThat(pedidos.contar()).isEqualTo(2);                 // solo ana; luis se revirtió completo
    }

    @Test
    void correccionConTransactionTemplate() {
        assertThatThrownBy(() -> corregido.importarConTemplate(LOTE)).hasMessageContaining("luis");
        assertThat(pedidos.contar()).isEqualTo(2);
    }

    @Test
    void unMetodoDePaqueteTambienEsTransaccionalSiSeLlamaPorElProxy() {
        assertThatThrownBy(() -> servicio.guardarDePaquete(new PedidoDto("zoe", -1))).isInstanceOf(IllegalArgumentException.class);
        assertThat(pedidos.contar()).isZero();                      // Spring 6+: no hace falta que sea público
    }

    // 2
    @Test
    void excepcionComprobadaNoRevierteSalvoConRollbackFor() {
        assertThatThrownBy(() -> comprobadas.conDefecto("ana")).isInstanceOf(ExcepcionesComprobadas.SaldoInsuficiente.class);
        assertThat(pedidos.contar()).isEqualTo(1);                  // quedó guardado
        assertThatThrownBy(() -> comprobadas.corregido("luis")).isInstanceOf(ExcepcionesComprobadas.SaldoInsuficiente.class);
        assertThat(pedidos.contar()).isEqualTo(1);                  // el corregido sí revirtió
    }

    // 3
    @Test
    void unexpectedRollbackException() {
        assertThatThrownBy(() -> reversion.externoQueCaptura())
                .isInstanceOf(UnexpectedRollbackException.class)
                .hasMessageContaining("rollback-only");
        assertThat(pedidos.contar()).isZero();                      // tampoco quedó lo del externo
    }

    // 4
    @Test
    void aspectoDeAuditoria() {
        auditoria.registros.clear();
        UsuarioActual.es("ana");
        try {
            cuentas.bloquear("001");
            cuentas.consultar("001");
            assertThatThrownBy(() -> cuentas.bloquear(" ")).hasMessage("cuenta vacía");
        } finally {
            UsuarioActual.limpiar();
        }
        assertThat(auditoria.registros).containsExactly("ana ejecutó bloquear cuenta: OK", "ana ejecutó bloquear cuenta: FALLÓ (cuenta vacía)");
    }

    // 5
    @Test
    void laClaseDelBeanEsUnaSubclaseGenerada() {
        System.out.println("servicio.getClass() = " + servicio.getClass().getName());
        assertThat(servicio.getClass().getName()).startsWith("transacciones.ServicioPedidos$$SpringCGLIB$$");
        assertThat(servicio.getClass().getSuperclass()).isEqualTo(ServicioPedidos.class);
        assertThat(AopUtils.isCglibProxy(servicio)).isTrue();
    }

    // 6
    @Test
    void requiredFrenteARequiresNewParaAuditar() {
        assertThatThrownBy(() -> reversion.operarConAuditoria(false)).hasMessage("la operación falla");
        assertThatThrownBy(() -> reversion.operarConAuditoria(true)).hasMessage("la operación falla");
        assertThat(pedidos.auditorias("intento-required")).isZero();     // se perdió con la operación
        assertThat(pedidos.auditorias("intento-new")).isEqualTo(1);      // sobrevivió: transacción propia
        assertThat(pedidos.contar()).isZero();
    }
}
