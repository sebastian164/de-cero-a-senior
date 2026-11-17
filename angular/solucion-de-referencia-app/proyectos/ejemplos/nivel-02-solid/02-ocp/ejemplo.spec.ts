import { render, screen } from '@testing-library/angular';
import { NotificacionComponent, PROVEEDORES_DE_NOTIFICACIONES, TarjetaComponent } from './notificaciones';
import { Component } from '@angular/core';

describe('n2-02 OCP (después)', () => {
  it('el registro elige el componente por tipo y le pasa la notificación como "datos"', async () => {
    await render(NotificacionComponent, { inputs: { n: { tipo: 'pedido', numero: 'A-17' } }, providers: PROVEEDORES_DE_NOTIFICACIONES });
    expect(screen.getByText('Pedido A-17 confirmado')).toHaveClass('azul');
  });
  it('un tipo sin vista registrada no pinta nada', async () => {
    const { container } = await render(NotificacionComponent, { inputs: { n: { tipo: 'otro' } }, providers: PROVEEDORES_DE_NOTIFICACIONES });
    expect(container.textContent?.trim()).toBe('');
  });
  it('la tarjeta proyecta título, cuerpo y acciones en su sitio', async () => {
    @Component({ imports: [TarjetaComponent], template: `<app-tarjeta><b acciones>A</b><i>C</i><h2 titulo>T</h2></app-tarjeta>` })
    class Uso {}
    const { container } = await render(Uso);
    expect(container.querySelector('header')?.textContent).toBe('T');
    expect(container.querySelector('footer')?.textContent).toBe('A');
    expect(container.querySelector('section > i')?.textContent).toBe('C');
  });
});
