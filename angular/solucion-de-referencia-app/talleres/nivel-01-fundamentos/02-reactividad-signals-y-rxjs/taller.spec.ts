import { TestBed } from '@angular/core/testing';
import { mergeMap, switchMap } from 'rxjs';
import { TestScheduler } from 'rxjs/testing';
import { ivaDe } from './carrito';
import { ALMACENAMIENTO, CarritoStore, CLAVE_CARRITO } from './carrito.store';
import { buscador, guardarSinDobleEnvio } from './flujos';

class AlmacenamientoEnMemoria implements Storage {
  private datos = new Map<string, string>();
  get length() { return this.datos.size; }
  clear() { this.datos.clear(); }
  getItem(k: string) { return this.datos.get(k) ?? null; }
  key(i: number) { return [...this.datos.keys()][i] ?? null; }
  removeItem(k: string) { this.datos.delete(k); }
  setItem(k: string, v: string) { this.datos.set(k, v); }
}

const programador = () => new TestScheduler((real, esperado) => expect(real).toEqual(esperado));

describe('Taller n1-02', () => {
  describe('carrito con señales', () => {
    let almacen: AlmacenamientoEnMemoria;
    beforeEach(() => {
      almacen = new AlmacenamientoEnMemoria();
      TestBed.configureTestingModule({ providers: [{ provide: ALMACENAMIENTO, useValue: almacen }] });
    });

    it('subtotal, iva, total y cantidadDeItems son derivados', () => {
      const c = TestBed.inject(CarritoStore);
      c.agregar({ productoId: 1, nombre: 'Teclado', precio: 100000 });
      c.agregar({ productoId: 1, nombre: 'Teclado', precio: 100000 });
      c.agregar({ productoId: 4, nombre: 'Mouse', precio: 40000 });
      expect(c.cantidadDeItems()).toBe(3);
      expect(c.subtotal()).toBe(240000);
      expect(c.iva()).toBe(ivaDe(240000));
      expect(c.total()).toBe(285600);
    });

    it('el efecto persiste en el almacenamiento y un carrito nuevo lo recupera', () => {
      const c = TestBed.inject(CarritoStore);
      c.agregar({ productoId: 4, nombre: 'Mouse', precio: 40000 });
      TestBed.tick();                                             // los efectos corren en la detección de cambios
      expect(JSON.parse(almacen.getItem(CLAVE_CARRITO)!)).toHaveLength(1);
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({ providers: [{ provide: ALMACENAMIENTO, useValue: almacen }] });
      expect(TestBed.inject(CarritoStore).cantidadDeItems()).toBe(1);
    });
  });

  describe('buscador con latencia variable', () => {
    // 'te' tarda 50 ms; 'tecl' tarda 5 ms. 'tecl' se escribe después.
    const latencias: Record<string, number> = { te: 50, tecl: 5 };

    it('3. con switchMap el resultado final es el de la última búsqueda: no hay carrera', () => {
      programador().run(({ cold, hot, expectObservable }) => {
        const buscar = (t: string) => cold(`${'-'.repeat(latencias[t] * 10)}(r|)`, { r: [t] });
        const textos = hot('a 310ms b', { a: 'te', b: 'tecl' });
        // 'te' sale a los 300 ms (respondería a los 800) y se cancela cuando sale 'tecl' (311 + 300 = 611 ms).
        expectObservable(buscador(buscar, switchMap)(textos)).toBe('661ms r', { r: ['tecl'] });
      });
    });

    it('5. con mergeMap se reproduce el error: la respuesta vieja llega al final y pisa a la nueva', () => {
      programador().run(({ cold, hot, expectObservable }) => {
        const buscar = (t: string) => cold(`${'-'.repeat(latencias[t] * 10)}(r|)`, { r: [t] });
        const textos = hot('a 310ms b', { a: 'te', b: 'tecl' });
        // 'te' responde a los 300+500 = 800 ms; 'tecl' a los 611+50 = 661 ms. Lo último que ve el usuario es 'te'.
        expectObservable(buscador(buscar, mergeMap)(textos)).toBe('661ms a 138ms b', { a: ['tecl'], b: ['te'] });
      });
    });
  });

  it('4. exhaustMap ignora los clics mientras el guardado está en curso', () => {
    let llamadas = 0;
    programador().run(({ cold, hot, expectObservable }) => {
      const guardar = () => { llamadas++; return cold('---(o|)', { o: 'ok' }); };
      const clics = hot<void>('a-a-------a');                       // clics en 0, 2 y 10; cada guardado tarda 3
      expectObservable(guardarSinDobleEnvio(guardar)(clics)).toBe('3ms o 9ms o', { o: 'ok' });
    });
    expect(llamadas).toBe(2);
  });

  describe('un fallo no mata el flujo (catchError dentro)', () => {
    it('el buscador devuelve una lista vacía ante un error y atiende la búsqueda siguiente', () => {
      programador().run(({ cold, hot, expectObservable }) => {
        const buscar = (q: string) => (q === 'mal' ? cold<string[]>('#') : cold('(r|)', { r: [q] }));
        const textos = hot('a 400ms b 400ms c', { a: 'mal', b: 'bien', c: 'otro' });
        expectObservable(buscador(buscar)(textos)).toBe('300ms e 400ms r 400ms s', { e: [], r: ['bien'], s: ['otro'] });
      });
    });
    it('un guardado fallido avisa y el siguiente clic vuelve a guardar', () => {
      const avisos: unknown[] = [];
      programador().run(({ cold, hot, expectObservable }) => {
        let n = 0;
        const guardar = () => (n++ === 0 ? cold('--#', undefined, 'boom') : cold('--(o|)', { o: 'ok' }));
        expectObservable(guardarSinDobleEnvio(guardar, (e) => avisos.push(e))(hot<void>('a----a'))).toBe('7ms o', { o: 'ok' });
      });
      expect(avisos).toEqual(['boom']);
    });
  });
});
