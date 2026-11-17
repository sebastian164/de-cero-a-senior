// Solución del taller final de frontend-senior (liderazgo): LIDERAZGO.md con los siete entregables y el contrato ejecutable.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { ErrorApi, RespuestaListado, responderSimulacro, simulacros } from './contrato';

const doc = readFileSync(join(__dirname, 'LIDERAZGO.md'), 'utf8');
const seccion = (n: number) => doc.split(new RegExp(`^## ${n}\\. `, 'm'))[1].split(/^## \d\. /m)[0];
const filas = (t: string) =>
  t
    .split('\n')
    .filter((l) => /^\| /.test(l) && !/^\| ---/.test(l))
    .slice(1);

describe('1. radar', () => {
  const f = filas(seccion(1));
  it('tiene diez elementos', () => expect(f).toHaveLength(10));
  it('cada uno tiene anillo válido y una justificación de al menos 8 palabras', () => {
    for (const l of f) {
      const c = l
        .split('|')
        .map((x) => x.trim())
        .filter(Boolean);
      expect(['Adoptar', 'Probar', 'Evaluar', 'Retener']).toContain(c[1]);
      expect(c[2].split(/\s+/).length).toBeGreaterThanOrEqual(8);
    }
  });
});
describe('2. ADR', () => {
  const adrs = seccion(2).split(/^### /m).slice(1);
  it('hay tres con contexto, decisión, alternativas y consecuencias', () => {
    expect(adrs).toHaveLength(3);
    for (const a of adrs) for (const c of ['Estado', 'Contexto', 'Decisión', 'Alternativas', 'Consecuencias']) expect(a).toContain(`**${c}**`);
  });
});
describe('3. definición de terminado', () => {
  const f = filas(seccion(3));
  it('indica en cada punto si se verifica de forma automática y con qué', () => {
    expect(f.length).toBeGreaterThanOrEqual(8);
    for (const l of f) expect(l).toMatch(/\| (Automático|Manual) \|/);
    expect(f.filter((l) => l.includes('| Automático |')).length).toBeGreaterThanOrEqual(5);
  });
});
describe('4. contrato y simulacros', () => {
  it('los simulacros de éxito cumplen el esquema del contrato', () => {
    expect(RespuestaListado.safeParse(simulacros.ok.cuerpo).success).toBe(true);
    expect(RespuestaListado.safeParse(simulacros.vacio.cuerpo).success).toBe(true);
  });
  it('los simulacros de error cumplen el esquema de error', () => {
    for (const s of [simulacros.sinSesion, simulacros.parametroInvalido, simulacros.falla]) expect(ErrorApi.safeParse(s.cuerpo).success).toBe(true);
  });
  it('el simulacro filtra por estado y rechaza un estado desconocido con 400', () => {
    expect(responderSimulacro('pagado').estado).toBe(200);
    expect(RespuestaListado.parse(responderSimulacro('enviado').cuerpo).items).toHaveLength(0);
    expect(responderSimulacro('inventado').estado).toBe(400);
  });
  it('una respuesta que rompe el contrato se detecta', () => {
    expect(RespuestaListado.safeParse({ items: [{ id: 'x', estado: 'otro', total: -1, creadoEn: 'ayer' }], siguiente: null }).success).toBe(false);
  });
  it('el documento incluye el contrato y los casos', () => {
    for (const t of ['GET /pedidos', '401', '400', 'siguiente']) expect(seccion(4)).toContain(t);
  });
});
describe('5. negociación', () => {
  it('tiene costo, riesgo y dos alternativas', () => {
    const s = seccion(5);
    for (const t of ['Costo', 'Riesgo', 'Alternativa A', 'Alternativa B']) expect(s).toContain(t);
  });
});
describe('6. defensa de diez minutos', () => {
  it('el guion cabe en diez minutos a 130 palabras por minuto, e incluye problema, decisiones, alternativas, costos y evolución', () => {
    const s = seccion(6);
    const palabras = s.split(/\s+/).filter(Boolean).length;
    expect(palabras).toBeLessThanOrEqual(1300);
    expect(palabras).toBeGreaterThan(300);
    for (const t of ['Problema', 'Decisiones', 'Alternativas', 'Costos', 'Evolución']) expect(s).toContain(t);
  });
});
describe('7. balance', () => {
  it('cubre todos los niveles de la ruta y un plan a seis meses', () => {
    const s = seccion(7);
    for (let n = 1; n <= 6; n++) expect(s).toContain(`Nivel ${n}`);
    for (const m of ['Mes 1', 'Mes 2', 'Mes 3', 'Mes 4', 'Mes 5', 'Mes 6']) expect(s).toContain(m);
  });
});
