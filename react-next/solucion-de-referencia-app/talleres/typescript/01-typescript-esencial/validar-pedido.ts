// Punto 4: validador de unknown a Pedido, sin 'as'. Devuelve un resultado, no lanza.
export interface LineaPedido {
  readonly producto: string;
  readonly cantidad: number;
  readonly precio: number;
}
export interface Pedido {
  readonly id: string;
  readonly cliente: string;
  readonly estado: 'pendiente' | 'pagado' | 'cancelado';
  readonly lineas: readonly LineaPedido[];
  readonly creadoEn: Date;
}
export type Resultado<T> = { ok: true; valor: T } | { ok: false; error: string };

const ESTADOS = ['pendiente', 'pagado', 'cancelado'] as const;
type Estado = (typeof ESTADOS)[number];

const esObjeto = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
const esEstado = (v: unknown): v is Estado => typeof v === 'string' && ESTADOS.some((e) => e === v);

function validarLinea(v: unknown, i: number): Resultado<LineaPedido> {
  if (!esObjeto(v)) return { ok: false, error: `lineas[${i}] no es un objeto` };
  const { producto, cantidad, precio } = v;
  if (typeof producto !== 'string' || producto.length === 0) return { ok: false, error: `lineas[${i}].producto debe ser texto` };
  if (typeof cantidad !== 'number' || !Number.isInteger(cantidad) || cantidad <= 0) return { ok: false, error: `lineas[${i}].cantidad debe ser un entero positivo` };
  if (typeof precio !== 'number' || precio < 0) return { ok: false, error: `lineas[${i}].precio debe ser un número no negativo` };
  return { ok: true, valor: { producto, cantidad, precio } };
}

export function validarPedido(entrada: unknown): Resultado<Pedido> {
  if (!esObjeto(entrada)) return { ok: false, error: 'La respuesta no es un objeto' };
  const { id, cliente, estado, lineas, creadoEn } = entrada;
  if (typeof id !== 'string') return { ok: false, error: 'id debe ser texto' };
  if (typeof cliente !== 'string') return { ok: false, error: 'cliente debe ser texto' };
  if (!esEstado(estado)) return { ok: false, error: `estado inválido: ${String(estado)}` };
  if (!Array.isArray(lineas)) return { ok: false, error: 'lineas debe ser un arreglo' };
  if (typeof creadoEn !== 'string' || Number.isNaN(Date.parse(creadoEn))) return { ok: false, error: 'creadoEn debe ser una fecha ISO' };
  const validadas: LineaPedido[] = [];
  for (const [i, linea] of lineas.entries()) {
    const r = validarLinea(linea, i);
    if (!r.ok) return r;
    validadas.push(r.valor);
  }
  return { ok: true, valor: { id, cliente, estado, lineas: validadas, creadoEn: new Date(creadoEn) } };
}
