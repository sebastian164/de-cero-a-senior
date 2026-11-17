import { registrarMedicion } from '../../../scripts/medicion';
import { describe, expect, it } from 'vitest';
import * as p from './puras';
import * as a from './asincronia';

const tareas = (): p.Tarea[] => [
  { id: 1, titulo: 'b', hecha: false, etiquetas: [] },
  { id: 2, titulo: 'a', hecha: false, etiquetas: ['x'] },
];
const perfil = (): p.Perfil => ({ nombre: 'Ana', direccion: { ciudad: 'Bogotá', calle: 'Cra 7' } });

describe('TS 03 · punto 1: puras frente a impuras', () => {
  it('las impuras modifican el original (por eso se reescriben)', () => {
    const t = tareas();
    p.marcarHechaImpura(t, 1);
    expect(t[0]?.hecha).toBe(true);
  });
  it('marcarHecha no modifica el original', () => {
    const t = tareas(); const copia = structuredClone(t);
    expect(p.marcarHecha(t, 1)[0]?.hecha).toBe(true);
    expect(t).toEqual(copia);
  });
  it('ordenar no modifica el original', () => {
    const t = tareas(); const copia = structuredClone(t);
    expect(p.ordenar(t).map((x) => x.titulo)).toEqual(['a', 'b']);
    expect(t).toEqual(copia);
  });
  it('quitar no modifica el original', () => {
    const t = tareas(); const copia = structuredClone(t);
    expect(p.quitar(t, 1)).toHaveLength(1);
    expect(t).toEqual(copia);
  });
  it('etiquetar no modifica el original', () => {
    const t = tareas()[1]!; const copia = structuredClone(t);
    expect(p.etiquetar(t, 'y').etiquetas).toEqual(['x', 'y']);
    expect(t).toEqual(copia);
  });
  it('mudar no modifica el original, ni el objeto anidado', () => {
    const per = perfil(); const copia = structuredClone(per);
    expect(p.mudar(per, 'Cali').direccion.ciudad).toBe('Cali');
    expect(per).toEqual(copia);
  });
});

describe('TS 03 · punto 2: copia superficial', () => {
  it('con ... el objeto anidado se comparte', () => {
    const original = perfil();
    const copia = p.copiaSuperficial(original);
    copia.direccion.ciudad = 'Medellín';
    expect(original.direccion.ciudad).toBe('Medellín');
    expect(copia.direccion).toBe(original.direccion);
  });
  it('copiando el segundo nivel ya no se comparte', () => {
    const original = perfil();
    const copia = p.copiaDosNiveles(original);
    copia.direccion.ciudad = 'Medellín';
    expect(original.direccion.ciudad).toBe('Bogotá');
  });
});

describe('TS 03 · punto 3: serie frente a Promise.all', () => {
  it('en serie ≈ 1.200 ms; en paralelo ≈ 500 ms', async () => {
    const serie = await a.enSerie();
    const paralelo = await a.enParalelo();
    registrarMedicion('ts-03 serie frente a Promise.all', { serieMs: Math.round(serie.ms), paraleloMs: Math.round(paralelo.ms) });
    expect(serie.valores).toEqual(paralelo.valores);
    expect(serie.ms).toBeGreaterThanOrEqual(1190);
    expect(paralelo.ms).toBeGreaterThanOrEqual(490);
    expect(paralelo.ms).toBeLessThan(800);
  });
});

describe('TS 03 · punto 4: condición de carrera', () => {
  it('el buscador ingenuo termina mostrando un término viejo', async () => {
    const b = a.crearBuscadorIngenuo();
    for (const t of ['c', 'ca', 'caf', 'cafe']) b.teclear(t);
    await b.esperarTodo();
    expect(b.pantalla.mostrado).toBe('resultados de "c"');
  });
  it('con AbortController se muestra el último término', async () => {
    const b = a.crearBuscadorConCancelacion();
    for (const t of ['c', 'ca', 'caf', 'cafe']) b.teclear(t);
    await b.esperarTodo();
    expect(b.pantalla.mostrado).toBe('resultados de "cafe"');
    expect(b.pantalla.canceladas).toBe(3);
  });
});

describe('TS 03 · punto 5: bucle de eventos', () => {
  it('síncrono, luego microtareas, luego tareas', async () => {
    expect(await a.ordenDelBucle()).toEqual(['1 síncrono', '2 síncrono', '3 promesa (microtarea)', '4 setTimeout (tarea)']);
  });
});
