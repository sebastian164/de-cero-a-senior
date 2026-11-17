// @vitest-environment node
// 2. Sin navegador: este archivo corre en el entorno "node" de Vitest (no hay window, document ni localStorage).
import { Injector } from '@angular/core';
import { Avisador, AvisadorEnPagina, decidir, Metricas, RecordatoriosRemotos, RegistroDeAvisos, RegistroEnMemoria, Recordatorios, RELOJ } from './recordatorios';

const AHORA = Date.UTC(2026, 10, 15, 12, 0, 0);
const armar = () => {
  const remotos: string[] = [];
  const injector = Injector.create({
    providers: [
      { provide: RELOJ, useValue: () => AHORA },
      { provide: RegistroDeAvisos, useClass: RegistroEnMemoria, deps: [] },
      { provide: Avisador, useClass: AvisadorEnPagina, deps: [] },
      { provide: Metricas, useValue: { recordatorioEnviado: () => {} } },
      { provide: RecordatoriosRemotos, useValue: { registrar: async (id: string) => { remotos.push(id); } } },
      { provide: Recordatorios, useClass: Recordatorios, deps: [] },
    ],
  });
  return { servicio: injector.get(Recordatorios), avisador: injector.get(Avisador) as AvisadorEnPagina, remotos };
};

describe('Recordatorios sin navegador y con tiempo fijo', () => {
  it('no hay DOM en este entorno', () => {
    expect(typeof window).toBe('undefined');
    expect(typeof localStorage).toBe('undefined');
  });
  it('decisión pura', () => {
    expect(decidir(AHORA + 3_600_000, AHORA, false)).toBe('avisar');
    expect(decidir(AHORA + 3_600_000, AHORA, true)).toBe('pendiente');
    expect(decidir(AHORA + 48 * 3_600_000, AHORA, false)).toBe('pendiente');
    expect(decidir(AHORA - 1, AHORA, false)).toBe('vencida');
  });
  it('avisa una sola vez por cita, registra en el backend y deja la métrica', async () => {
    const { servicio, avisador, remotos } = armar();
    const cita = new Date(AHORA + 2 * 3_600_000).toISOString();
    expect(await servicio.programar('c1', cita)).toBe('avisada');
    expect(await servicio.programar('c1', cita)).toBe('pendiente');
    expect(avisador.avisos).toEqual(['Tu cita es mañana']);
    expect(remotos).toEqual(['c1']);
  });
});
