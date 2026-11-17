import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CacheServidor } from './cache-servidor';

const diferida = <T>() => { let ok!: (v: T) => void; let mal!: (e: unknown) => void; const p = new Promise<T>((a, b) => { ok = a; mal = b; }); return { p, ok, mal }; };
const esperar = () => new Promise((r) => setTimeout(r, 0));

describe('n5-02 CacheServidor (estado de servidor)', () => {
  let t = 0;
  const crear = (vigenciaMs = 1000) => new CacheServidor({ vigenciaMs, ahora: () => t });
  beforeEach(() => { t = 0; });

  it('deduplica: dos lectores de la misma clave, una sola petición', async () => {
    const cache = crear(); const d = diferida<string[]>(); const buscar = vi.fn(() => d.p);
    const a = TestBed.runInInjectionContext(() => cache.leer('x', buscar));
    const b = TestBed.runInInjectionContext(() => cache.leer('x', buscar));
    expect(buscar).toHaveBeenCalledTimes(1);
    expect(a().cargando).toBe(true);
    d.ok(['uno']); await esperar();
    expect(a().datos).toEqual(['uno']); expect(b().datos).toEqual(['uno']); expect(a().cargando).toBe(false);
  });

  it('mientras es vigente no vuelve a pedir; al vencer sirve lo guardado y revalida en segundo plano', async () => {
    const cache = crear(1000); let n = 0; const buscar = vi.fn(() => Promise.resolve(++n));
    const e = cache.leer('x', buscar); await esperar();
    t = 999; cache.leer('x', buscar); expect(buscar).toHaveBeenCalledTimes(1);          // vigente
    t = 1001; cache.leer('x', buscar);
    expect(buscar).toHaveBeenCalledTimes(2);                                              // vencida: revalida
    expect(e().datos).toBe(1);                                                            // mientras tanto, el dato viejo
    expect(e().cargando).toBe(true);
    await esperar();
    expect(e().datos).toBe(2);
  });

  it('invalidar tras una mutación vuelve a pedir y actualiza a todos los lectores', async () => {
    const cache = crear(10_000); const servidor = ['a'];
    const buscar = vi.fn(() => Promise.resolve([...servidor]));
    const e1 = cache.leer('productos', buscar); const e2 = cache.leer('productos', buscar); await esperar();
    await cache.mutar(() => { servidor.push('b'); return of(undefined); }, ['productos']);
    await esperar();
    expect(buscar).toHaveBeenCalledTimes(2);
    expect(e1().datos).toEqual(['a', 'b']); expect(e2().datos).toEqual(['a', 'b']);
  });

  it('una mutación que falla no invalida', async () => {
    const cache = crear(); const buscar = vi.fn(() => Promise.resolve(1));
    cache.leer('x', buscar); await esperar();
    await expect(cache.mutar(() => Promise.reject(new Error('500')), ['x'])).rejects.toThrow('500');
    expect(buscar).toHaveBeenCalledTimes(1);
  });

  it('una respuesta vieja no pisa a la de después de invalidar (carrera)', async () => {
    const cache = crear(); const viejo = diferida<string>(); const nuevo = diferida<string>();
    const respuestas = [viejo.p, nuevo.p]; const buscar = vi.fn(() => respuestas.shift()!);
    const e = cache.leer('x', buscar);
    cache.invalidar('x');                       // invalida mientras la primera sigue en vuelo
    nuevo.ok('nuevo'); await esperar();
    viejo.ok('viejo'); await esperar();         // la vieja llega tarde
    expect(e().datos).toBe('nuevo');
  });

  it('un error queda en la entrada y se puede reintentar leyendo otra vez', async () => {
    const cache = crear(); let falla = true;
    const buscar = () => (falla ? Promise.reject(new Error('503')) : Promise.resolve('ok'));
    const e = cache.leer('x', buscar); await esperar();
    expect(String(e().error)).toContain('503'); expect(e().datos).toBeUndefined();
    falla = false; cache.leer('x', buscar); await esperar();
    expect(e().datos).toBe('ok'); expect(e().error).toBeUndefined();
  });
});
