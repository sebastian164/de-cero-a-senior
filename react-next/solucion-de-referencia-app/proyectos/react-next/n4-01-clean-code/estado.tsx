'use client';
// ---- react-next/nivel-04-clean-code-y-testing/01-clean-code-en-react/README.md (bloque 1), copiado tal cual entre el contexto agregado ----
import { useState } from 'react';

export interface Pedido { readonly id: string }

export function EstadoConBanderas() {   // contexto: las tres banderas del "Antes" viven en un componente
// Antes: ocho combinaciones, la mayoría sin sentido
const [cargando, setCargando] = useState(false);
const [error, setError] = useState<string | null>(null);
const [datos, setDatos] = useState<Pedido[] | null>(null);
  return <p>{cargando ? 'Cargando' : error ?? datos?.length}</p>;
}

// Después: solo los estados que existen
type Estado =
  | { tipo: 'inactivo' }
  | { tipo: 'cargando' }
  | { tipo: 'error'; mensaje: string }
  | { tipo: 'listo'; pedidos: readonly Pedido[] };

export type { Estado };
