import { TestBed } from '@angular/core/testing';
import { AlmacenDeCarrito, AlmacenEnMemoria, Analitica, CarritoService, RELOJ } from './carrito';

describe('n2-05 DIP (después)', () => {
  it('la prueba del README: sin navegador, con tiempo fijo', () => {
    const productoAgregado = vi.fn();
    // En una prueba
    TestBed.configureTestingModule({
      providers: [
        { provide: AlmacenDeCarrito, useClass: AlmacenEnMemoria },
        { provide: Analitica, useValue: { productoAgregado } },
        { provide: RELOJ, useValue: () => 1_700_000_000_000 },
      ],
    });
    TestBed.inject(CarritoService).agregar({ id: 'p1', nombre: 'Teclado', precio: 100 });
    expect(TestBed.inject(AlmacenDeCarrito).leer()).toEqual([{ id: 'p1', nombre: 'Teclado', precio: 100, agregadoEn: 1_700_000_000_000 }]);
    expect(productoAgregado).toHaveBeenCalledWith('p1');
  });
});
