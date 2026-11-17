import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { Pedido, TablaPedidosComponent, llamadas } from './tabla-pedidos.component';

@Component({ imports: [TablaPedidosComponent], template: `<table><app-tabla-pedidos [pedidos]="pedidos()" /></table><p>{{ otro() }}</p>` })
class Anfitrion {
  readonly pedidos = signal<readonly Pedido[]>([{ id: 1, lineas: [{ precio: 10, cantidad: 2 }] }, { id: 2, lineas: [{ precio: 5, cantidad: 1 }] }]);
  readonly otro = signal(0);
}

describe('n6-01 ejemplo: computed + OnPush', () => {
  it('el total de cada fila se calcula una vez por lista, no en cada revisión', async () => {
    llamadas.total = 0;
    const f = TestBed.createComponent(Anfitrion);
    await f.whenStable();
    expect(llamadas.total).toBe(2);
    for (let i = 1; i <= 5; i++) { f.componentInstance.otro.set(i); await f.whenStable(); }   // revisiones ajenas a la tabla
    expect(llamadas.total).toBe(2);
    f.componentInstance.pedidos.update((p) => [...p, { id: 3, lineas: [{ precio: 1, cantidad: 1 }] }]);
    await f.whenStable();
    expect(llamadas.total).toBe(5);                                                           // lista nueva: se recalculan las 3 filas
    expect(f.nativeElement.querySelectorAll('tr').length).toBe(3);
  });
});
