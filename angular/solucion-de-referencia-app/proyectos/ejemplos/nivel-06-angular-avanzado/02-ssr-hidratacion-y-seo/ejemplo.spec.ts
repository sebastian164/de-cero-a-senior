import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { Ancho } from './ancho.component';
import { ALMACEN_LOCAL, AlmacenEnMemoria } from './almacen';

describe('n6-02 ejemplo', () => {
  it('el ancho se lee solo tras el primer renderizado', async () => {
    const f = TestBed.createComponent(Ancho);
    await f.whenStable();
    expect(f.nativeElement.querySelector('output').textContent).toBe(String(window.innerWidth));
  });
  it('sin navegador, el almacén es una implementación en memoria', () => {
    const a = TestBed.inject(ALMACEN_LOCAL);
    expect(a).toBeInstanceOf(AlmacenEnMemoria);
    a.escribir('x', '1');
    expect(a.leer('x')).toBe('1');
    expect(TestBed.inject(ALMACEN_LOCAL).leer('y')).toBeNull();
  });
});
