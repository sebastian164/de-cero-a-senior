// Solución del taller de frontend-senior/02 (lo que se prueba sin navegador): banderas, colector, source maps y los flujos de integración continua.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { SourceMapGenerator } from 'source-map';
import { parse } from 'yaml';
import { describe, expect, it } from 'vitest';
import { cubo, estaActiva } from './bandera';
import { listarErrores, listarMetricas, registrarError, registrarMetrica } from './colector';
import { marcosDe, simbolizar } from './simbolizar';
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';

describe('punto 6: bandera de función por porcentaje', () => {
  const ids = Array.from({ length: 20_000 }, (_, i) => `usuario-${i}`);
  const activos = (p: number) => ids.filter((id) => estaActiva('nueva-ficha', id, p));
  it('0 % no libera a nadie y 100 % libera a todos', () => {
    expect(activos(0)).toHaveLength(0);
    expect(activos(100)).toHaveLength(ids.length);
  });
  it('un porcentaje libera aproximadamente esa fracción', () => {
    for (const p of [5, 25, 50, 80]) expect(Math.abs(activos(p).length / ids.length - p / 100)).toBeLessThan(0.01);
  });
  it('es estable: la misma persona obtiene siempre lo mismo', () => {
    for (const id of ids.slice(0, 200)) expect(estaActiva('nueva-ficha', id, 30)).toBe(estaActiva('nueva-ficha', id, 30));
  });
  it('subir el porcentaje solo suma: nadie que la tenía la pierde', () => {
    const al10 = new Set(activos(10));
    const al40 = new Set(activos(40));
    for (const id of al10) expect(al40.has(id)).toBe(true);
    expect(al40.size).toBeGreaterThan(al10.size);
  });
  it('banderas distintas reparten distinto (no liberan siempre a las mismas personas)', () => {
    const a = new Set(ids.filter((id) => estaActiva('a', id, 30)));
    const b = ids.filter((id) => estaActiva('b', id, 30));
    expect(b.filter((id) => a.has(id)).length / b.length).toBeLessThan(0.5);
  });
  it('valores raros de porcentaje no rompen: NaN apaga, negativos apagan, mayores de 100 encienden', () => {
    expect(estaActiva('x', 'u', Number.NaN)).toBe(false);
    expect(estaActiva('x', 'u', -5)).toBe(false);
    expect(estaActiva('x', 'u', 250)).toBe(true);
    expect(cubo('x', 'u')).toBeGreaterThanOrEqual(0);
    expect(cubo('x', 'u')).toBeLessThan(100);
  });
});

describe('punto 3 y 4: colector de errores y métricas', () => {
  it('guarda versión, ruta y traza, y recorta lo largo', () => {
    registrarError({ mensaje: 'x'.repeat(900), ruta: '/a', version: '1.2.3', pila: 'p'.repeat(9000) });
    const ultimo = listarErrores().at(-1)!;
    expect(ultimo.mensaje).toHaveLength(500);
    expect(ultimo.pila).toHaveLength(4000);
    expect([ultimo.ruta, ultimo.version]).toEqual(['/a', '1.2.3']);
  });
  it('rechaza reportes con datos personales', () => {
    for (const campo of ['clave', 'password', 'token', 'cookie', 'correo', 'email', 'documento']) {
      expect(() => registrarError({ mensaje: 'm', [campo]: 'secreto' })).toThrow('datos personales');
    }
  });
  it('rechaza reportes mal formados', () => {
    expect(() => registrarError(null)).toThrow();
    expect(() => registrarError({ mensaje: 42 })).toThrow('Reporte inválido');
  });
  it('un error esperado (validación, sin permiso) no se registra como fallo', () => {
    const antes = listarErrores().length;
    registrarError({ mensaje: 'Correo inválido', esperado: true });
    expect(listarErrores()).toHaveLength(antes);
  });
  it('las métricas web exigen nombre, valor numérico y ruta', () => {
    registrarMetrica({ nombre: 'LCP', valor: 1234, ruta: '/x', id: 'v1', calificacion: 'good' });
    expect(listarMetricas().at(-1)).toMatchObject({ nombre: 'LCP', valor: 1234 });
    expect(() => registrarMetrica({ nombre: 'LCP', valor: '1', ruta: '/x' })).toThrow('Métrica inválida');
  });
});

describe('punto 3: source maps', () => {
  it('extrae los marcos de una traza de producción', () => {
    const pila = 'Error: x\n    at E (http://127.0.0.1:49510/_next/static/chunks/abc.js:1:3025)\n    at http://127.0.0.1:49510/_next/static/chunks/def.js:2:10';
    expect(marcosDe(pila)).toEqual([
      { url: 'http://127.0.0.1:49510/_next/static/chunks/abc.js', linea: 1, columna: 3025 },
      { url: 'http://127.0.0.1:49510/_next/static/chunks/def.js', linea: 2, columna: 10 },
    ]);
  });
  it('vuelve de la posición minificada a la fuente original usando el mapa', async () => {
    const raiz = mkdtempSync(join(tmpdir(), 'static-'));
    mkdirSync(join(raiz, 'chunks'));
    const g = new SourceMapGenerator({ file: 'abc.js' });
    g.addMapping({ generated: { line: 1, column: 3024 }, original: { line: 7, column: 8 }, source: 'src/fe/falla.ts', name: 'provocarFallaNoControlada' });
    writeFileSync(join(raiz, 'chunks', 'abc.js.map'), g.toString());
    const r = await simbolizar('Error: x\n at E (http://127.0.0.1:49510/_next/static/chunks/abc.js:1:3025)', raiz);
    expect(r).toEqual([{ fuente: 'src/fe/falla.ts', linea: 7, columna: 8, nombre: 'provocarFallaNoControlada' }]);
  });
  it('un mapa que falta no rompe la simbolización', async () => {
    expect(await simbolizar('at http://x/_next/static/chunks/nada.js:1:1', '/ruta/que/no/existe')).toEqual([]);
  });
});

describe('punto 1 y 2: los flujos de integración continua', () => {
  const paquete = JSON.parse(readFileSync('package.json', 'utf8')) as { scripts: Record<string, string> };
  const leer = (n: string) =>
    parse(readFileSync(join(__dirname, 'ci', n), 'utf8')) as {
      on: unknown;
      jobs: Record<string, { 'runs-on'?: string; needs?: string; steps?: { uses?: string; run?: string; if?: string }[] }>;
    };

  it('calidad.yml: cada trabajo tiene máquina y pasos, clona el repositorio e instala dependencias', () => {
    const { jobs } = leer('calidad.yml');
    expect(Object.keys(jobs)).toEqual(['calidad', 'e2e', 'desplegar']);
    for (const [nombre, j] of Object.entries(jobs)) {
      expect(j['runs-on'], `${nombre}: runs-on`).toBeTruthy();
      expect(j.steps?.length, `${nombre}: pasos`).toBeGreaterThan(0);
      if (nombre !== 'desplegar') {
        expect(j.steps![0]!.uses, `${nombre}: checkout`).toMatch(/^actions\/checkout@/);
        expect(
          j.steps!.some((p) => p.run === 'npm ci'),
          `${nombre}: npm ci`,
        ).toBe(true);
      }
    }
    expect(jobs.e2e!.needs).toBe('calidad');
    expect(jobs.desplegar!.needs).toBe('e2e');
  });
  it('calidad.yml cubre todas las puertas de la tabla del README y cada script npm que usa existe', () => {
    const comandos = Object.values(leer('calidad.yml').jobs)
      .flatMap((j) => (j.steps ?? []).map((p) => p.run ?? ''))
      .filter(Boolean);
    const todo = comandos.join('\n');
    for (const [puerta, patron] of [
      ['linter (a11y y límites)', /npm run lint/],
      ['tipos estrictos', /npm run typecheck/],
      ['formateador', /npm run formato/],
      ['pruebas unitarias y de componentes', /npm test/],
      ['pruebas e2e (y accesibilidad con axe)', /npx playwright test/],
      ['presupuesto de tamaño', /npm run build/],
      ['auditoría de dependencias', /npm audit --audit-level=high/],
      ['presupuesto de rendimiento (Lighthouse)', /npm run lighthouse/],
    ] as const)
      expect(todo, puerta).toMatch(patron);
    for (const c of comandos) {
      const m = /^npm run ([\w:-]+)/.exec(c);
      if (m) expect(paquete.scripts[m[1]!], `script npm "${m[1]}"`).toBeTruthy();
    }
    expect(paquete.scripts['build']).toContain('presupuesto');
    expect(paquete.scripts.auditoria).toContain('--audit-level=high');
    expect(paquete.scripts.e2e).toBe('playwright test');
  });
  it('vista-previa.yml: se dispara en cada pull request, levanta al abrir o actualizar y baja al cerrar', () => {
    const { on, jobs } = leer('vista-previa.yml') as unknown as { on: { pull_request: { types: string[] } }; jobs: Record<string, { steps: { run?: string; if?: string }[] }> };
    expect(on.pull_request.types).toEqual(['opened', 'synchronize', 'reopened', 'closed']);
    const pasos = jobs['vista-previa']!.steps;
    expect(pasos.find((p) => p.run?.includes('levantar'))!.if).toContain("!= 'closed'");
    expect(pasos.find((p) => p.run?.includes('bajar'))!.if).toContain("== 'closed'");
  });
  it('calidad.yml: los trabajos que corren pruebas con PostgreSQL declaran el servicio y la variable de conexión', () => {
    const { jobs } = leer('calidad.yml') as unknown as { jobs: Record<string, { services?: Record<string, { image: string; ports: string[] }>; env?: Record<string, string> }> };
    for (const nombre of ['calidad', 'e2e']) {
      expect(jobs[nombre]!.services?.postgres?.image, `${nombre}: servicio postgres`).toMatch(/^postgres:17/);
      expect(jobs[nombre]!.services!.postgres!.ports).toContain('49540:5432');
      expect(jobs[nombre]!.env?.PEDIDOS_BD_URL, `${nombre}: PEDIDOS_BD_URL`).toContain('49540/pedidos');
    }
  });
  it('lighthouse-ci.mjs arranca y apaga su propio servidor cuando no se le da BASE_URL', () => {
    const codigo = readFileSync(join(__dirname, '..', '..', '..', 'scripts', 'lighthouse-ci.mjs'), 'utf8');
    expect(codigo).toMatch(/spawn\('npx', \['next', 'start'/);
    expect(codigo).toMatch(/servidor\?\.kill/);
  });
  it('vista-previa.yml: corre en un ejecutor propio (la vista previa debe seguir viva) y publica la URL en el pull request', () => {
    const { jobs } = leer('vista-previa.yml');
    const j = jobs['vista-previa']!;
    expect(j['runs-on']).toBe('self-hosted');
    expect((j.steps ?? []).some((p) => p.run?.includes('gh pr comment'))).toBe(true);
  });
});
