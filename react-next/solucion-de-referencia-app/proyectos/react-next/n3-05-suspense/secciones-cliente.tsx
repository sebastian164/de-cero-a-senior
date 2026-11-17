'use client';
// Contexto: sección de cliente que suspende con use() y falla en el primer intento (para ver el límite de error).
// Cuando el límite reporta el fallo, el siguiente intento (al pulsar Reintentar) crea una petición nueva que sí resuelve.
import { use } from 'react';
import { alReportar } from './reporte';

let intento = 1;
const promesas = new Map<number, Promise<string[]>>();

alReportar((m) => { if (m === 'Fallo simulado al cargar pedidos') intento++; });

function cargar(): Promise<string[]> {
  const n = intento;
  if (!promesas.has(n)) {
    const promesa = new Promise<string[]>((resolver, rechazar) =>
      setTimeout(() => (n === 1 ? rechazar(new Error('Fallo simulado al cargar pedidos')) : resolver(['P-1 pendiente', 'P-2 pagado'])), 300));
    promesa.catch(() => {});
    promesas.set(n, promesa);
  }
  return promesas.get(n)!;
}

export function PedidosDelClienteCliente() {
  const pedidos = use(cargar());
  return <ul>{pedidos.map((p) => <li key={p}>{p}</li>)}</ul>;
}

export const EsqueletoTabla = () => <div aria-hidden="true">{[1, 2].map((i) => <div key={i} className="esqueleto" style={{ height: 24 }} />)}</div>;
