import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { of } from 'rxjs';
import { AlmacenPedidos, ListaPedidosComponent, Pedido, PedidosApi, total, unPedido } from './pedidos';

// Los cuatro ejemplos del README, tal cual (con el import que les falta).
describe('total', () => {
  it('suma los importes de las líneas y aplica el IVA', () => {
    const pedido = unPedido({ lineas: [{ precio: 100, cantidad: 2 }, { precio: 50, cantidad: 1 }] });
    expect(total(pedido)).toBe(297.5);
  });
});

describe('AlmacenPedidos', () => {
  it('expone solo los pedidos del filtro activo', () => {
    const api = { listar: () => of([unPedido({ estado: 'pendiente' }), unPedido({ estado: 'pagado' })]) };
    TestBed.configureTestingModule({ providers: [{ provide: PedidosApi, useValue: api }] });
    const almacen = TestBed.inject(AlmacenPedidos);

    almacen.cargar();
    almacen.filtrar('pendiente');

    expect(almacen.visibles()).toHaveLength(1);
  });
});

it('traduce la respuesta de la API al modelo', () => {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  const api = TestBed.inject(PedidosApi);
  const http = TestBed.inject(HttpTestingController);
  let resultado: Pedido[] = [];

  api.listar().subscribe((pedidos) => (resultado = pedidos));
  http.expectOne('/api/pedidos').flush([{ id: 7, estado: 1, valor_centavos: 12000, /* ... */ }]);

  expect(resultado[0]).toMatchObject({ id: '7', estado: 'pendiente', total: 120 });
  http.verify();
});

describe('ListaPedidosComponent', () => {
  it('avisa al padre cuando se cancela un pedido', async () => {
    const cancelar = vi.fn();
    await render(ListaPedidosComponent, {
      inputs: { pedidos: [unPedido({ id: 'p1', cliente: 'Ana' })] },
      on: { cancelar },
    });

    await userEvent.click(screen.getByRole('button', { name: /cancelar/i }));

    expect(cancelar).toHaveBeenCalledWith('p1');
  });

  it('muestra un mensaje cuando no hay pedidos', async () => {
    await render(ListaPedidosComponent, { inputs: { pedidos: [] } });
    expect(screen.getByText('No hay pedidos.')).toBeInTheDocument();
  });
});
