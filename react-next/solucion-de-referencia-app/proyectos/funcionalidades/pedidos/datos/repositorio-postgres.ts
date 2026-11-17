// Contexto (nivel-05/03): el adaptador repositorioPostgres que la composición del README usa. PostgreSQL real.
import 'server-only';
import { Pool } from 'pg';
import type { Pedido } from '../dominio/pedido';
import type { RepositorioPedidos } from '../dominio/puertos';

const URL_BD = process.env.PEDIDOS_BD_URL ?? 'postgres://verificacion:verificacion@127.0.0.1:49540/pedidos';
const global_ = globalThis as unknown as { poolPedidos?: Pool; esquemaListo?: Promise<void> };
const pool = (global_.poolPedidos ??= new Pool({ connectionString: URL_BD, max: 5 }));

const hace = (horas: number) => new Date(Date.now() - horas * 3_600_000);
export const SEMILLA: readonly Pedido[] = [
  { id: 'P-100', clienteId: 'u-1', estado: 'pendiente', creadoEn: hace(1), total: 32000 },
  { id: 'P-101', clienteId: 'u-1', estado: 'pagado', creadoEn: hace(3), total: 9000 },
  { id: 'P-102', clienteId: 'u-1', estado: 'pagado', creadoEn: hace(30), total: 85000 },
  { id: 'P-200', clienteId: 'u-2', estado: 'pendiente', creadoEn: hace(2), total: 64000 },
];

export function prepararEsquema(): Promise<void> {
  return (global_.esquemaListo ??= (async () => {
    await pool.query(`CREATE TABLE IF NOT EXISTS pedidos (
      id text PRIMARY KEY, cliente_id text NOT NULL, estado text NOT NULL CHECK (estado IN ('pendiente','pagado','cancelado')),
      creado_en timestamptz NOT NULL, total integer NOT NULL)`);
    for (const p of SEMILLA) {
      await pool.query('INSERT INTO pedidos VALUES ($1,$2,$3,$4,$5) ON CONFLICT (id) DO NOTHING', [p.id, p.clienteId, p.estado, p.creadoEn, p.total]);
    }
  })());
}

interface Fila { id: string; cliente_id: string; estado: Pedido['estado']; creado_en: Date; total: number }
const aPedido = (f: Fila): Pedido => ({ id: f.id, clienteId: f.cliente_id, estado: f.estado, creadoEn: new Date(f.creado_en), total: f.total });

export const repositorioPostgres: RepositorioPedidos = {
  async buscar(id) {
    await prepararEsquema();
    const { rows } = await pool.query<Fila>('SELECT * FROM pedidos WHERE id = $1', [id]);
    return rows[0] ? aPedido(rows[0]) : null;
  },
  async guardar(pedido) {
    await prepararEsquema();
    await pool.query(`INSERT INTO pedidos VALUES ($1,$2,$3,$4,$5)
      ON CONFLICT (id) DO UPDATE SET cliente_id = EXCLUDED.cliente_id, estado = EXCLUDED.estado, creado_en = EXCLUDED.creado_en, total = EXCLUDED.total`,
      [pedido.id, pedido.clienteId, pedido.estado, pedido.creadoEn, pedido.total]);
  },
};

/** Consulta de lectura (lado de consultas): no pasa por el caso de uso. */
export async function listarDePostgres(clienteId: string, estado?: string): Promise<readonly Pedido[]> {
  await prepararEsquema();
  const { rows } = await pool.query<Fila>(
    'SELECT * FROM pedidos WHERE cliente_id = $1 AND ($2::text IS NULL OR estado = $2) ORDER BY id', [clienteId, estado ?? null]);
  return rows.map(aPedido);
}
export async function cerrarPool() { await pool.end(); global_.poolPedidos = undefined; global_.esquemaListo = undefined; }
