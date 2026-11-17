// 4. El almacén se prueba sin componentes, con un doble de la API.
import { TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';
import { AlmacenCarrito, CarritoApi } from './almacen-carrito';

describe('AlmacenCarrito', () => {
  let respuestas: Subject<unknown>[];
  let almacen: AlmacenCarrito;
  beforeEach(() => {
    respuestas = [];
    const doble = { guardar: (): Observable<unknown> => { const s = new Subject<unknown>(); respuestas.push(s); return s; } };
    TestBed.configureTestingModule({ providers: [{ provide: CarritoApi, useValue: doble }] });
    almacen = TestBed.inject(AlmacenCarrito);
  });
  const ok = () => { const r = respuestas.shift()!; r.next({}); r.complete(); };

  it('agregar, cambiar cantidad, quitar y vaciar; derivados al día', () => {
    almacen.agregar({ productoId: 't', nombre: 'Teclado', precio: 100 }); ok();
    almacen.agregar({ productoId: 't', nombre: 'Teclado', precio: 100 }); ok();
    almacen.agregar({ productoId: 'm', nombre: 'Mouse', precio: 50 }); ok();
    expect(almacen.cantidad()).toBe(3);
    expect(almacen.subtotal()).toBe(250);
    expect(almacen.total()).toBe(298);
    almacen.cambiarCantidad('t', 5); ok();
    expect(almacen.subtotal()).toBe(550);
    almacen.cambiarCantidad('m', 0); ok();
    expect(almacen.lineas().map((l) => l.productoId)).toEqual(['t']);
    almacen.vaciar(); ok();
    expect(almacen.cantidad()).toBe(0);
  });

  it('3. optimista: se ve en el acto y se deshace si el guardado falla', () => {
    almacen.agregar({ productoId: 't', nombre: 'Teclado', precio: 100 }); ok();
    almacen.quitar('t');
    expect(almacen.cantidad()).toBe(0);                 // antes de que responda el servidor
    expect(almacen.guardando()).toBe(true);
    respuestas.shift()!.error(new Error('503'));
    expect(almacen.cantidad()).toBe(1);                 // reversa
    expect(almacen.error()).toMatch(/se deshizo/);
  });
});
