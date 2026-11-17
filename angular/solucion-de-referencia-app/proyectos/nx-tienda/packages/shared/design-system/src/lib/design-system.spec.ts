import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DsBoton, DsEtiqueta, DsTarjeta } from '../index';

@Component({
  imports: [DsBoton, DsEtiqueta, DsTarjeta],
  template: `
    <ds-tarjeta titulo="Pedido 7">
      <ds-etiqueta tono="exito">Pagado</ds-etiqueta>
      <ds-boton variante="peligro" (pulsado)="pulsos = pulsos + 1">Cancelar</ds-boton>
      <ds-boton [deshabilitado]="true">Imposible</ds-boton>
    </ds-tarjeta>
  `,
})
class Ejemplo { pulsos = 0; }

describe('sistema de diseño', () => {
  it('los componentes son elementos semánticos y se componen', async () => {
    const f = TestBed.createComponent(Ejemplo);
    await f.whenStable();
    const el: HTMLElement = f.nativeElement;
    expect(el.querySelector('article h3')?.textContent).toBe('Pedido 7');
    expect(el.querySelector('ds-etiqueta span')?.getAttribute('data-tono')).toBe('exito');
    const botones = el.querySelectorAll('button');
    expect(botones[0].className).toContain('ds-boton--peligro');
    botones[0].click();
    expect(f.componentInstance.pulsos).toBe(1);
    expect(botones[1].disabled).toBe(true);
    botones[1].click();
    expect(f.componentInstance.pulsos).toBe(1);
  });
});
