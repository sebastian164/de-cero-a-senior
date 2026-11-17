import { TestBed } from '@angular/core/testing';
import { CarritoServicio, Linea } from './carrito.servicio';
import { CarritoStore } from './carrito.store';

const l = (productoId: string, precio: number, cantidad: number): Linea => ({ productoId, precio, cantidad });

// El mismo conjunto de pruebas para las dos implementaciones: la comparación de facilidad de prueba es directa.
describe.each([
  ['servicio con señales', () => { TestBed.configureTestingModule({ providers: [CarritoServicio] }); return TestBed.inject(CarritoServicio); }],
  ['NgRx SignalStore', () => { TestBed.configureTestingModule({ providers: [CarritoStore] }); return TestBed.inject(CarritoStore); }],
] as const)('n5-02 carrito como %s', (_n, crear) => {
  it('empieza vacío', () => { const c = crear(); expect(c.lineas()).toEqual([]); expect(c.total()).toBe(0); expect(c.cantidad()).toBe(0); });
  it('suma total y cantidad', () => { const c = crear(); c.agregar(l('a', 100, 2)); c.agregar(l('b', 50, 1)); expect(c.total()).toBe(250); expect(c.cantidad()).toBe(3); });
  it('agrupa el mismo producto', () => { const c = crear(); c.agregar(l('a', 100, 1)); c.agregar(l('a', 100, 2)); expect(c.lineas()).toEqual([l('a', 100, 3)]); });
  it('quita un producto y vacía', () => { const c = crear(); c.agregar(l('a', 100, 1)); c.agregar(l('b', 10, 1)); c.quitar('a'); expect(c.lineas()).toEqual([l('b', 10, 1)]); c.vaciar(); expect(c.cantidad()).toBe(0); });
  it('es inmutable: las líneas anteriores no cambian', () => { const c = crear(); c.agregar(l('a', 100, 1)); const antes = c.lineas(); c.agregar(l('a', 100, 1)); expect(antes).toEqual([l('a', 100, 1)]); });
});

describe('n5-02 lo que distingue a los dos', () => {
  it('desde fuera nadie puede escribir el estado: el compilador lo impide en ambos', () => {
    TestBed.configureTestingModule({ providers: [CarritoServicio, CarritoStore] });
    const servicio = TestBed.inject(CarritoServicio);
    const store = TestBed.inject(CarritoStore);
    // @ts-expect-error `lineas` del servicio es de solo lectura (asReadonly)
    void servicio.lineas.set;
    // @ts-expect-error el estado del store tampoco se escribe sin pasar por `patchState`
    void store.lineas.set;
    expect(servicio.lineas()).toEqual([]);
  });
});
