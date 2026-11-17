import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { AlmacenPedidos, ListaPedidosComponent, PaginaPedidosComponent, Sesion } from './pedidos';
import { TestBed } from '@angular/core/testing';

describe('n3-01 contenedor y presentación (ejemplo)', () => {
  it('la presentación se prueba solo con entradas y eventos', async () => {
    const cancelar = vi.fn();
    await render(ListaPedidosComponent, { inputs: { pedidos: [{ id: 'x', cliente: 'Ana', urgente: true }], soloLectura: false }, on: { cancelar } });
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(cancelar).toHaveBeenCalledWith('x');
    expect(screen.getByRole('article')).toHaveClass('urgente');
  });
  it('el contenedor conecta el almacén y la sesión', async () => {
    await render(PaginaPedidosComponent);
    await userEvent.click(screen.getAllByRole('button', { name: 'Cancelar' })[0]);
    expect(screen.queryByText('Ana')).toBeNull();
    TestBed.inject(Sesion).puedeEditar.set(false);
    await new Promise((r) => setTimeout(r));
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeDisabled();
    expect(TestBed.inject(AlmacenPedidos).pendientes()).toHaveLength(1);
  });
});
