// Bandera de función con liberación por porcentaje. Determinista: la misma persona siempre cae en el mismo cubo (0 a 99),
// y subir el porcentaje solo suma personas (nadie que ya la tenía la pierde).
import { createHash } from 'node:crypto';

export function cubo(nombre: string, usuarioId: string): number {
  const h = createHash('sha256').update(`${nombre}:${usuarioId}`).digest();
  return h.readUInt32BE(0) % 100;
}

export function estaActiva(nombre: string, usuarioId: string, porcentaje: number): boolean {
  if (!Number.isFinite(porcentaje)) return false;
  return cubo(nombre, usuarioId) < Math.min(100, Math.max(0, Math.floor(porcentaje)));
}

// Configuración en memoria (en producción vendría de un servicio de banderas o de variables de entorno), modificable sin desplegar.
const g = globalThis as unknown as { banderas?: Record<string, number> };
const banderas = () => (g.banderas ??= { 'nueva-ficha': 0 });
export const porcentajeDe = (nombre: string) => banderas()[nombre] ?? 0;
export function fijarPorcentaje(nombre: string, porcentaje: number) {
  if (!(nombre in banderas())) throw new Error('Bandera desconocida');
  banderas()[nombre] = Math.min(100, Math.max(0, Math.floor(porcentaje)));
}
