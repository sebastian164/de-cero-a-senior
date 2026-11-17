import { TestBed } from '@angular/core/testing';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { FragmentosComponent, PadreCantidadComponent } from './padre.component';

describe('n1-03 SelectorCantidadComponent (después)', () => {
  it('[(cantidad)] avisa al padre, respeta los límites y la etiqueta es derivada', async () => {
    await render(PadreCantidadComponent);
    const u = userEvent.setup();
    const [menos, mas] = screen.getAllByRole('button').slice(0, 2);
    expect(menos).toBeDisabled();
    await u.click(mas); await u.click(mas);
    expect(screen.getByText('Vas a comprar 3')).toBeInTheDocument();
    expect(screen.getByText('3 de 3')).toBeInTheDocument();
    expect(mas).toBeDisabled();
    expect(screen.getAllByRole('status')[0]).toHaveTextContent('Llegaste al máximo.');
    const campo = screen.getByRole('spinbutton', { name: 'Cantidad' }) as HTMLInputElement;
    campo.value = '99'; campo.dispatchEvent(new Event('change'));
    await new Promise((r) => setTimeout(r));
    expect(screen.getByText('Vas a comprar 3')).toBeInTheDocument();      // 99 se recorta al máximo
    campo.value = '-4'; campo.dispatchEvent(new Event('change'));
    await new Promise((r) => setTimeout(r));
    expect(screen.getByText('Vas a comprar 1')).toBeInTheDocument();      // y -4 al mínimo
  });

  it('maximo="5" en la plantilla llega como número gracias a numberAttribute', async () => {
    await render(PadreCantidadComponent);
    expect(screen.getByText('1 de 5')).toBeInTheDocument();
  });

  it('afterNextRender enfoca el campo cuando ya está en pantalla', async () => {
    const { fixture } = await render(PadreCantidadComponent);
    await fixture.whenStable();
    expect(document.activeElement?.getAttribute('aria-label')).toMatch(/Cantidad|Con texto/);
  });

  it('@let y linkedSignal: el valor elegido vuelve a la primera opción si cambian las opciones', async () => {
    const { fixture } = await render(FragmentosComponent, { inputs: { subtotal: 80, envio: 30, opciones: ['a', 'b'] } });
    expect(screen.getByText('Total: 110')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Supera tu cupo.');
    fixture.componentInstance.elegir('b');
    await fixture.whenStable();
    expect(screen.getByText('Elegida: b')).toBeInTheDocument();
    fixture.componentRef.setInput('opciones', ['x', 'y']);
    await fixture.whenStable();
    expect(screen.getByText('Elegida: x')).toBeInTheDocument();
  });
});
