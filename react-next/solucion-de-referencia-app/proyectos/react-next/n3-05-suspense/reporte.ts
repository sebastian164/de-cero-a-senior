// Contexto: reportar(error) del README. Envía el error al colector propio sin datos personales.
export const reportados: string[] = [];
const oyentes = new Set<(mensaje: string) => void>();
/** Permite que otra parte (p. ej. un recurso) reaccione cuando se reporta un error. */
export function alReportar(oyente: (mensaje: string) => void) { oyentes.add(oyente); return () => oyentes.delete(oyente); }

export function reportar(error: unknown) {
  const mensaje = error instanceof Error ? error.message : String(error);
  reportados.push(mensaje);
  oyentes.forEach((o) => o(mensaje));
  if (typeof navigator !== 'undefined' && 'sendBeacon' in navigator && typeof window !== 'undefined' && window.location.protocol.startsWith('http')) {
    navigator.sendBeacon('/api/errores', JSON.stringify({ mensaje, ruta: window.location.pathname, esperado: false }));
  }
}
