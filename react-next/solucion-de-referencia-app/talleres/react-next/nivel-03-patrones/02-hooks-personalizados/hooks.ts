'use client';
// Punto 1: useInterruptor, useAlmacenLocal, useDebounce y useVisibleEnPantalla.
import { useCallback, useEffect, useRef, useState } from 'react';

export { useAlmacenLocal } from '../../nivel-01-fundamentos/02-hooks-y-efectos/hooks'; // ya escrito y probado en nivel-01/02

export function useInterruptor(inicial = false) {
  const [activo, setActivo] = useState(inicial);
  const alternar = useCallback(() => setActivo((a) => !a), []);
  const activar = useCallback(() => setActivo(true), []);
  const desactivar = useCallback(() => setActivo(false), []);
  return { activo, alternar, activar, desactivar } as const;
}

export function useDebounce<T>(valor: T, ms: number): T {
  const [retrasado, setRetrasado] = useState(valor);
  useEffect(() => {
    const t = setTimeout(() => setRetrasado(valor), ms);
    return () => clearTimeout(t);
  }, [valor, ms]);
  return retrasado;
}

/** Devuelve una ref para el elemento y si está visible. Desconecta el observador al desmontar. */
export function useVisibleEnPantalla<T extends Element>(opciones?: { umbral?: number; margen?: string; unaVez?: boolean }) {
  const ref = useRef<T>(null);
  const [visible, setVisible] = useState(false);
  const { umbral = 0, margen = '0px', unaVez = false } = opciones ?? {};
  useEffect(() => {
    const elemento = ref.current;
    if (!elemento) return;
    const observador = new IntersectionObserver(([entrada]) => {
      const ahora = entrada?.isIntersecting ?? false;
      setVisible(ahora);
      if (ahora && unaVez) observador.disconnect();
    }, { threshold: umbral, rootMargin: margen });
    observador.observe(elemento);
    return () => observador.disconnect();
  }, [umbral, margen, unaVez]);
  return [ref, visible] as const;
}

/* Punto 2: useFormulario mínimo. */
export type Validador<V> = (valores: V) => Partial<Record<keyof V, string>>;

export function useFormulario<V extends Record<string, string>>(iniciales: V, validar: Validador<V>, alEnviar: (v: V) => void | Promise<void>) {
  const [valores, setValores] = useState(iniciales);
  const [tocado, setTocado] = useState<Partial<Record<keyof V, boolean>>>({});
  const [enviando, setEnviando] = useState(false);
  const errores = validar(valores); // derivado: no es estado

  function campo(nombre: keyof V & string) {
    return {
      name: nombre,
      value: valores[nombre],
      onChange: (e: React.ChangeEvent<HTMLInputElement>) => setValores((v) => ({ ...v, [nombre]: e.target.value })),
      onBlur: () => setTocado((t) => ({ ...t, [nombre]: true })),
      error: tocado[nombre] ? errores[nombre] : undefined,
    };
  }

  async function enviar(e: React.FormEvent) {
    e.preventDefault();
    setTocado(Object.fromEntries(Object.keys(valores).map((k) => [k, true])) as Record<keyof V, boolean>);
    if (Object.keys(errores).length > 0) return;
    setEnviando(true);
    try { await alEnviar(valores); } finally { setEnviando(false); }
  }

  return { valores, errores, tocado, enviando, campo, enviar };
}
