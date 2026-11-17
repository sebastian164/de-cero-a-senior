'use client';
// Puntos 1 y 2: useAlmacenLocal y useEnLinea, con limpieza y tipos.
import { useCallback, useMemo, useSyncExternalStore } from 'react';

const EVENTO_LOCAL = 'almacen-local';

function suscribirAlmacen(avisar: () => void) {
  // 'storage' llega cuando otra pestaña cambia el valor; EVENTO_LOCAL, cuando lo cambia esta misma.
  window.addEventListener('storage', avisar);
  window.addEventListener(EVENTO_LOCAL, avisar);
  return () => {
    window.removeEventListener('storage', avisar);
    window.removeEventListener(EVENTO_LOCAL, avisar);
  };
}

function leerTexto(clave: string): string | null {
  try {
    return window.localStorage.getItem(clave);
  } catch {
    return null; // almacenamiento bloqueado
  }
}

/**
 * Estado sincronizado con localStorage. useSyncExternalStore se suscribe y se desuscribe (limpieza) y usa
 * `inicial` durante el renderizado en servidor y la hidratación, así que no hay diferencias de hidratación.
 */
export function useAlmacenLocal<T>(clave: string, inicial: T, validar?: (v: unknown) => v is T) {
  const texto = useSyncExternalStore(suscribirAlmacen, () => leerTexto(clave), () => null);

  const valor = useMemo<T>(() => {
    if (texto === null) return inicial;
    try {
      const v: unknown = JSON.parse(texto);
      if (validar) return validar(v) ? v : inicial;
      return v as T; // sin validador se confía en lo que esta misma aplicación guardó
    } catch {
      return inicial;
    }
  }, [texto, inicial, validar]);

  const guardar = useCallback(
    (nuevo: T | ((previo: T) => T)) => {
      const siguiente = typeof nuevo === 'function' ? (nuevo as (p: T) => T)(valor) : nuevo;
      try {
        window.localStorage.setItem(clave, JSON.stringify(siguiente));
      } catch {
        /* lleno o bloqueado */
      }
      window.dispatchEvent(new Event(EVENTO_LOCAL));
    },
    [clave, valor],
  );

  return [valor, guardar] as const;
}

/** Conexión del navegador. useSyncExternalStore gestiona la suscripción y su limpieza. */
export function useEnLinea(): boolean {
  return useSyncExternalStore(suscribirConexion, () => navigator.onLine, () => true);
}

function suscribirConexion(avisar: () => void) {
  window.addEventListener('online', avisar);
  window.addEventListener('offline', avisar);
  return () => {
    window.removeEventListener('online', avisar);
    window.removeEventListener('offline', avisar);
  };
}
