// Solución del taller de nivel-05/03.
import { ESLint } from 'eslint';
import { Pool } from 'pg';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { crearCancelarPedido } from '@/funcionalidades/pedidos/aplicacion/cancelar-pedido';
import { sePuedeCancelar, type Pedido } from '@/funcionalidades/pedidos/dominio/pedido';
import type { RepositorioPedidos } from '@/funcionalidades/pedidos/dominio/puertos';
import { SEMILLA, cerrarPool, listarDePostgres, repositorioPostgres } from '@/funcionalidades/pedidos/datos/repositorio-postgres';
import { crearRepositorioMemoria } from './repositorio-memoria';
import { componer } from './composicion-seleccionable';

const AHORA = new Date('2026-04-02T12:00:00Z');
const hace = (h: number) => new Date(AHORA.getTime() - h * 3_600_000);
const pedido = (p: Partial<Pedido> = {}): Pedido => ({ id: 'X-1', clienteId: 'u-1', estado: 'pagado', creadoEn: hace(2), total: 1000, ...p });

describe('punto 3: el caso de uso se prueba sin Next.js ni base de datos', () => {
  const armar = (semilla: Pedido[]) => { const repo = crearRepositorioMemoria(semilla); return { repo, cancelar: crearCancelarPedido(repo, () => AHORA) }; };
  it('cancela un pedido propio dentro de plazo y lo guarda', async () => {
    const { repo, cancelar } = armar([pedido()]);
    expect(await cancelar('X-1', 'u-1')).toEqual({ ok: true });
    expect((await repo.buscar('X-1'))?.estado).toBe('cancelado');
  });
  it('un pedido ajeno es "no encontrado" y no se modifica', async () => {
    const { repo, cancelar } = armar([pedido()]);
    expect(await cancelar('X-1', 'u-2')).toEqual({ ok: false, error: 'Pedido no encontrado' });
    expect((await repo.buscar('X-1'))?.estado).toBe('pagado');
  });
  it('un pedido inexistente es "no encontrado"', async () => {
    expect(await armar([]).cancelar('nada', 'u-1')).toEqual({ ok: false, error: 'Pedido no encontrado' });
  });
  it('fuera de las 24 horas o ya cancelado: no se puede', async () => {
    const viejo = armar([pedido({ creadoEn: hace(30) })]);
    expect(await viejo.cancelar('X-1', 'u-1')).toEqual({ ok: false, error: 'El pedido ya no se puede cancelar' });
    const ya = armar([pedido({ estado: 'cancelado' })]);
    expect((await ya.cancelar('X-1', 'u-1')).ok).toBe(false);
  });
  it('regla pura: borde de 24 horas inclusivo', () => {
    expect(sePuedeCancelar(pedido({ creadoEn: hace(24) }), AHORA)).toBe(true);
    expect(sePuedeCancelar(pedido({ creadoEn: hace(24.01) }), AHORA)).toBe(false);
    expect(sePuedeCancelar(pedido({ estado: 'pendiente' }), AHORA)).toBe(true);
  });
});

/** Mismo contrato para los dos adaptadores: lo que el caso de uso espera del puerto. */
function contrato(nombre: string, crear: () => Promise<RepositorioPedidos>) {
  describe(`punto 4: contrato del puerto con el adaptador ${nombre}`, () => {
    let repo: RepositorioPedidos;
    beforeEach(async () => { repo = await crear(); });
    it('buscar devuelve null si no existe', async () => { expect(await repo.buscar('no-existe')).toBeNull(); });
    it('guardar y buscar devuelven el mismo pedido (con su fecha)', async () => {
      const p = pedido({ id: 'C-1', creadoEn: new Date('2026-04-01T10:00:00.000Z') });
      await repo.guardar(p);
      expect(await repo.buscar('C-1')).toEqual(p);
    });
    it('guardar sobre un id existente lo actualiza', async () => {
      await repo.guardar(pedido({ id: 'C-2' }));
      await repo.guardar(pedido({ id: 'C-2', estado: 'cancelado' }));
      expect((await repo.buscar('C-2'))?.estado).toBe('cancelado');
    });
    it('el caso de uso funciona igual con este adaptador', async () => {
      await repo.guardar(pedido({ id: 'C-3', creadoEn: hace(1) }));
      const cancelar = crearCancelarPedido(repo, () => AHORA);
      expect(await cancelar('C-3', 'u-1')).toEqual({ ok: true });
      expect((await repo.buscar('C-3'))?.estado).toBe('cancelado');
    });
  });
}
contrato('en memoria', async () => crearRepositorioMemoria());
contrato('PostgreSQL real', async () => {
  const p = new Pool({ connectionString: process.env.PEDIDOS_BD_URL ?? 'postgres://verificacion:verificacion@127.0.0.1:49540/pedidos' });
  await repositorioPostgres.buscar('calentar');                 // crea el esquema y la semilla
  await p.query("DELETE FROM pedidos WHERE id LIKE 'C-%'");
  await p.end();
  return repositorioPostgres;
});

describe('punto 4: cambiar de adaptador solo en la composición', () => {
  it('la composición elige memoria o PostgreSQL y el resto no cambia', async () => {
    const m = componer('memoria', repositorioPostgres, () => AHORA);
    expect(m.repositorio).not.toBe(repositorioPostgres);
    await m.repositorio.guardar(pedido({ id: 'M-1' }));
    expect(await m.cancelarPedidoCasoDeUso('M-1', 'u-1')).toEqual({ ok: true });
    expect(componer('postgres', repositorioPostgres).repositorio).toBe(repositorioPostgres);
  });
});

describe('lectura contra PostgreSQL (lado de consultas)', () => {
  afterAll(() => cerrarPool());
  it('lista solo los pedidos del usuario y filtra por estado validado', async () => {
    await repositorioPostgres.buscar('calentar');
    const ana = await listarDePostgres('u-1');
    expect(ana.every((p) => p.clienteId === 'u-1')).toBe(true);
    expect(ana.map((p) => p.id)).toEqual(expect.arrayContaining(SEMILLA.filter((s) => s.clienteId === 'u-1').map((s) => s.id)));
    expect((await listarDePostgres('u-1', 'pagado')).every((p) => p.estado === 'pagado')).toBe(true);
    expect(await listarDePostgres('u-2', 'cancelado')).toEqual([]);
  });
});

describe('punto 1: los límites de arquitectura los verifica el linter', () => {
  const eslint = new ESLint({ cwd: process.cwd() });
  const lint = async (ruta: string, codigo: string) => (await eslint.lintText(codigo, { filePath: ruta }))[0]!.messages.filter((m) => m.ruleId === 'no-restricted-imports').map((m) => m.message.replace(/^.*pattern\. /, ''));
  it('el dominio no importa React ni Next', async () => {
    expect(await lint('proyectos/funcionalidades/pedidos/dominio/x.ts', "import { useState } from 'react';\nexport const a = useState;"))
      .toEqual(['El dominio no depende de frameworks.']);
    expect(await lint('proyectos/funcionalidades/pedidos/dominio/x.ts', "import { cookies } from 'next/headers';\nexport const a = cookies;"))
      .toEqual(['El dominio no depende de frameworks.']);
  });
  it('el dominio no importa datos, acciones ni interfaz', async () => {
    expect(await lint('proyectos/funcionalidades/pedidos/dominio/x.ts', "import { x } from '../datos/repositorio-postgres';\nexport const a = x;"))
      .toEqual(['El dominio no importa datos, acciones ni interfaz.']);
  });
  it('el dominio real cumple las reglas', async () => {
    for (const f of ['pedido', 'puertos']) {
      const r = await eslint.lintFiles([`proyectos/funcionalidades/pedidos/dominio/${f}.ts`]);
      expect(r[0]!.messages.filter((m) => m.ruleId === 'no-restricted-imports')).toEqual([]);
    }
  });
  it('la capa de aplicación no importa React, Next, datos, acciones ni interfaz', async () => {
    const ruta = 'proyectos/funcionalidades/pedidos/aplicacion/x.ts';
    expect(await lint(ruta, "import { cookies } from 'next/headers';\nexport const a = cookies;")).toEqual(['El dominio no depende de frameworks.']);
    expect(await lint(ruta, "import { x } from '../datos/repositorio-postgres';\nexport const a = x;")).toEqual(['El dominio no importa datos, acciones ni interfaz.']);
    expect(await lint(ruta, "import { sePuedeCancelar } from '../dominio/pedido';\nexport const a = sePuedeCancelar;")).toEqual([]);   // lo permitido
  });
  it('nucleo (compartido) no importa funcionalidades', async () => {
    expect(await lint('proyectos/nucleo/x.ts', "import { a } from '../funcionalidades/pedidos';\nexport const b = a;")).toEqual(['compartido no importa funcionalidades.']);
    expect(await lint('proyectos/nucleo/x.ts', "import { cookies } from 'next/headers';\nexport const b = cookies;")).toEqual([]);
  });
  it('una funcionalidad no importa el interior de otra, solo su index', async () => {
    const ruta = 'proyectos/funcionalidades/facturas/ui/x.ts';
    expect(await lint(ruta, "import { p } from '@/funcionalidades/pedidos/dominio/pedido';\nexport const a = p;")).toEqual(['Importa otra funcionalidad solo por su index.ts.']);
    expect(await lint(ruta, "import { p } from '@/funcionalidades/pedidos';\nexport const a = p;")).toEqual([]);
  });
  it('todo el código real de funcionalidades y nucleo cumple las reglas', async () => {
    const r = await eslint.lintFiles(['proyectos/funcionalidades/**/*.{ts,tsx}', 'proyectos/nucleo/**/*.ts']);
    expect(r.length).toBeGreaterThanOrEqual(10);
    expect(r.flatMap((x) => x.messages.filter((m) => m.ruleId === 'no-restricted-imports'))).toEqual([]);
  });
});
