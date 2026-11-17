// Límite de tasa por ventana deslizante, en memoria (una réplica). Con varias réplicas iría en Redis o similar.
export interface Limitador { intentar(clave: string, ahora?: number): { permitido: boolean; reintentarEnMs: number } }

export function crearLimitador(maximo: number, ventanaMs: number): Limitador {
  const intentos = new Map<string, number[]>();
  return {
    intentar(clave, ahora = Date.now()) {
      const recientes = (intentos.get(clave) ?? []).filter((t) => ahora - t < ventanaMs);
      if (recientes.length >= maximo) {
        intentos.set(clave, recientes);
        return { permitido: false, reintentarEnMs: ventanaMs - (ahora - recientes[0]!) };
      }
      recientes.push(ahora);
      intentos.set(clave, recientes);
      return { permitido: true, reintentarEnMs: 0 };
    },
  };
}

// Un único limitador de ingreso por proceso (5 intentos por minuto). Vive fuera del archivo de la acción:
// un archivo 'use server' solo puede exportar acciones, y reiniciar los contadores no debe ser una de ellas.
const global_ = globalThis as unknown as { limiteIngreso?: Limitador };
export const limiteDeIngreso = (): Limitador => (global_.limiteIngreso ??= crearLimitador(5, 60_000));
/** Solo para las pruebas. */
export const reiniciarLimiteDeIngreso = () => { global_.limiteIngreso = undefined; };
