import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { PedidosI18n } from './pedidos.component';

describe('n6-04 ejemplo: plural ICU', () => {
  it('cero, uno y varios', async () => {
    const f = TestBed.createComponent(PedidosI18n);
    await f.whenStable();
    const texto = () => f.nativeElement.querySelector('[data-id=plural]').textContent.trim();
    const [cero, uno, cinco] = [...f.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];
    expect(texto()).toBe('No tienes pedidos');
    uno.click(); await f.whenStable();
    expect(texto()).toBe('Tienes un pedido');
    cinco.click(); await f.whenStable();
    expect(texto()).toBe('Tienes 5 pedidos');
    cero.click(); await f.whenStable();
    expect(texto()).toBe('No tienes pedidos');
  });
});
