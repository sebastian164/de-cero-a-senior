// Contexto: secciones de servidor de distinta velocidad para el ejemplo de Suspense del README.
import { connection } from 'next/server';

const esperar = (ms: number) => new Promise((r) => setTimeout(r, ms));

export async function EncabezadoCliente({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  await connection();
  await esperar(100);
  return <h2>Cliente {id}: Ana Gómez</h2>;
}

export async function PedidosDelCliente({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  await connection();
  await esperar(1200);
  return <table><tbody><tr><td>P-1</td><td>cliente {id}</td><td>pendiente</td></tr><tr><td>P-2</td><td>cliente {id}</td><td>pagado</td></tr></tbody></table>;
}

export const EsqueletoEncabezado = () => <div className="esqueleto" style={{ height: 32, width: 280 }} aria-hidden="true" />;
export const EsqueletoTabla = () => <div aria-hidden="true">{[1, 2].map((i) => <div key={i} className="esqueleto" style={{ height: 24 }} />)}</div>;
