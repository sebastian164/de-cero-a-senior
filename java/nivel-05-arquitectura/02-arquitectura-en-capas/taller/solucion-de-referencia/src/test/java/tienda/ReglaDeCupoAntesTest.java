package tienda;

import org.junit.jupiter.api.Test;
import tienda.antes.model.Cliente;
import tienda.antes.model.Pedido;
import tienda.antes.repository.PedidoRepository;
import tienda.antes.service.PedidoService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Antes: para probar la regla hay que simular el repositorio y su consulta. */
class ReglaDeCupoAntesTest {

    @Test
    void laReglaSoloSePruebaSimulandoElRepositorio() {
        Cliente ana = new Cliente();
        ana.setId(1L);
        ana.setCupo(1_000_000);
        Pedido pedido = new Pedido();
        pedido.setId(12L);
        pedido.setCliente(ana);
        pedido.setTotal(500_000);

        PedidoRepository repo = mock(PedidoRepository.class);
        when(repo.findById(12L)).thenReturn(Optional.of(pedido));
        when(repo.deudaDe(1L)).thenReturn(700_000L);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> new PedidoService(repo).confirmar(12L)).hasMessage("Cupo excedido");
    }
}
