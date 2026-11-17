import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, describe, expect, it } from 'vitest';
import { DemoComposicion } from './demo';
import { UsoPestanas } from '@/react-next/n3-01-composicion/uso-pestanas';

beforeAll(() => {
  // jsdom no implementa showModal/close de <dialog>: se completan con el comportamiento mínimo.
  HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) { this.removeAttribute('open'); };
});

describe('nivel-03/01 · punto 1: pestañas con teclado', () => {
  it('flechas, Inicio y Fin mueven el foco y activan la pestaña', async () => {
    const u = userEvent.setup();
    render(<DemoComposicion />);
    await u.click(screen.getByRole('tab', { name: 'Datos' }));
    await u.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: 'Pedidos' })).toHaveFocus();
    expect(screen.getByRole('tabpanel')).toHaveTextContent('2 pedidos pendientes');
    await u.keyboard('{End}');
    expect(screen.getByRole('tab', { name: 'Facturas' })).toHaveAttribute('aria-selected', 'true');
    await u.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: 'Datos' })).toHaveFocus();
    await u.keyboard('{ArrowLeft}');
    expect(screen.getByRole('tab', { name: 'Facturas' })).toHaveFocus();
    await u.keyboard('{Home}');
    expect(screen.getByRole('tabpanel')).toHaveTextContent('Ana Gómez');
    expect(screen.getAllByRole('tab').filter((t) => t.tabIndex === 0)).toHaveLength(1); // tabulación itinerante
  });
  it('el uso del README funciona con el componente del README', async () => {
    render(<UsoPestanas pendientes={2} />);
    expect(screen.getByRole('tabpanel')).toHaveTextContent('Ana Gómez');
    await userEvent.click(screen.getByRole('tab', { name: /Pedidos/ }));
    expect(screen.getByRole('tabpanel')).toHaveTextContent('P-1 pendiente');
  });
});

describe('nivel-03/01 · puntos 2 y 4: acordeón compuesto y de arreglo', () => {
  it('el compuesto abre uno a la vez y acepta contenido libre en el encabezado', async () => {
    const u = userEvent.setup();
    render(<DemoComposicion />);
    const envio = screen.getByRole('button', { name: 'Envío' });
    const pago = screen.getByRole('button', { name: /Pago/ });
    await u.click(envio);
    expect(envio).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('region', { name: 'Envío' })).toHaveTextContent('Llega en 2 días.');
    await u.click(pago);
    expect(envio).toHaveAttribute('aria-expanded', 'false');
    expect(pago).toHaveAttribute('aria-expanded', 'true');
  });
  it('el de arreglo con multiple deja varias secciones abiertas', async () => {
    const u = userEvent.setup();
    render(<DemoComposicion />);
    await u.click(screen.getByRole('button', { name: 'Garantía' }));
    await u.click(screen.getByRole('button', { name: 'Devoluciones' }));
    expect(screen.getByRole('button', { name: 'Garantía' })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: 'Devoluciones' })).toHaveAttribute('aria-expanded', 'true');
  });
});

describe('nivel-03/01 · punto 3: diálogo nativo', () => {
  it('abre, muestra los huecos y ejecuta la acción', async () => {
    const u = userEvent.setup();
    render(<DemoComposicion />);
    await u.click(screen.getByRole('button', { name: 'Eliminar cuenta' }));
    expect(screen.getByRole('dialog', { name: '¿Eliminar la cuenta?' })).toHaveTextContent('Esta acción no se puede deshacer.');
    await u.click(screen.getByRole('button', { name: 'Eliminar' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Cuenta eliminada');
  });
});
