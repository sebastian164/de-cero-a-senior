// Colector mínimo de errores y métricas del navegador. Rechaza campos con datos personales.
export interface ReporteError {
  mensaje: string;
  ruta?: string;
  version?: string;
  pila?: string;
  esperado?: boolean;
  traza?: string;
}
export interface Metrica {
  nombre: string;
  valor: number;
  ruta: string;
  id: string;
  calificacion?: string;
}

const errores: (ReporteError & { recibido: string })[] = [];
const metricas: Metrica[] = [];
const CAMPOS_PROHIBIDOS = ['clave', 'password', 'token', 'cookie', 'correo', 'email', 'documento'];

function sinDatosPersonales(o: Record<string, unknown>) {
  return Object.keys(o).every((k) => !CAMPOS_PROHIBIDOS.includes(k.toLowerCase()));
}

export function registrarError(datos: unknown) {
  if (typeof datos !== 'object' || datos === null || typeof (datos as ReporteError).mensaje !== 'string') throw new Error('Reporte inválido');
  if (!sinDatosPersonales(datos as Record<string, unknown>)) throw new Error('El reporte trae datos personales');
  const r = datos as ReporteError;
  if (r.esperado) return; // los errores esperados no son fallos: no se registran como tales
  errores.push({ mensaje: r.mensaje.slice(0, 500), ruta: r.ruta, version: r.version, pila: r.pila?.slice(0, 4000), traza: r.traza, recibido: new Date().toISOString() });
  if (errores.length > 500) errores.shift();
}
export const listarErrores = () => [...errores];

export function registrarMetrica(datos: unknown) {
  const m = datos as Metrica;
  if (typeof m?.nombre !== 'string' || typeof m.valor !== 'number' || typeof m.ruta !== 'string') throw new Error('Métrica inválida');
  metricas.push({ nombre: m.nombre, valor: m.valor, ruta: m.ruta, id: String(m.id), calificacion: m.calificacion });
  if (metricas.length > 5000) metricas.shift();
}
export const listarMetricas = () => [...metricas];

/** Percentil p (0-100) por nombre de métrica y ruta: así se miran los datos de campo. */
export function percentil(nombre: string, p: number, ruta?: string): number | null {
  const valores = metricas
    .filter((m) => m.nombre === nombre && (!ruta || m.ruta === ruta))
    .map((m) => m.valor)
    .toSorted((a, b) => a - b);
  if (valores.length === 0) return null;
  return valores[Math.min(valores.length - 1, Math.ceil((p / 100) * valores.length) - 1)]!;
}
