import { TestBed } from '@angular/core/testing';
import { render, screen } from '@testing-library/angular';
import { Observable, Subject } from 'rxjs';
import { BuscadorComponent } from './buscador.component';
import { Producto, ProductosService } from './productos.service';

describe('n1-02 BuscadorComponent (después)', () => {
  afterEach(() => vi.useRealTimers());

  it('espera 300 ms, pide una sola vez y cancela la petición anterior (sin carrera)', async () => {
    vi.useFakeTimers();
    const pendientes: { texto: string; sujeto: Subject<Producto[]>; cancelada: boolean }[] = [];
    const servicio = {
      buscar: (texto: string) => new Observable<Producto[]>((obs) => {
        const p = { texto, sujeto: new Subject<Producto[]>(), cancelada: false };
        pendientes.push(p);
        const s = p.sujeto.subscribe(obs);
        return () => { p.cancelada = true; s.unsubscribe(); };
      }),
    };
    await render(BuscadorComponent, { providers: [{ provide: ProductosService, useValue: servicio }] });
    const campo = screen.getByLabelText('Buscar') as HTMLInputElement;

    for (const t of ['t', 'te', 'tec']) { campo.value = t; campo.dispatchEvent(new Event('input')); await vi.advanceTimersByTimeAsync(100); }
    expect(pendientes).toHaveLength(0);                              // todavía escribiendo
    await vi.advanceTimersByTimeAsync(300);
    expect(pendientes.map((p) => p.texto)).toEqual(['tec']);         // una sola petición

    campo.value = 'te'; campo.dispatchEvent(new Event('input'));
    await vi.advanceTimersByTimeAsync(300);
    expect(pendientes[0].cancelada).toBe(true);                     // switchMap canceló 'tec'
    pendientes[0].sujeto.next([{ id: 9, nombre: 'Vieja' }]);         // llega tarde: se ignora
    pendientes[1].sujeto.next([{ id: 1, nombre: 'Teclado' }, { id: 3, nombre: 'Tetera' }]);
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();
    expect(screen.getByText('2 resultados')).toBeInTheDocument();
    expect(screen.queryByText('Vieja')).toBeNull();
  });

  it('con menos de dos letras no pide nada y un error deja la lista vacía', async () => {
    vi.useFakeTimers();
    const buscar = vi.fn(() => new Observable<Producto[]>((o) => o.error(new Error('500'))));
    await render(BuscadorComponent, { providers: [{ provide: ProductosService, useValue: { buscar } }] });
    const campo = screen.getByLabelText('Buscar') as HTMLInputElement;
    campo.value = 'm'; campo.dispatchEvent(new Event('input'));
    await vi.advanceTimersByTimeAsync(400);
    expect(buscar).not.toHaveBeenCalled();
    campo.value = 'mo'; campo.dispatchEvent(new Event('input'));
    await vi.advanceTimersByTimeAsync(400);
    TestBed.tick();
    expect(buscar).toHaveBeenCalledWith('mo');
    expect(screen.getByText('0 resultados')).toBeInTheDocument();
  });
});
