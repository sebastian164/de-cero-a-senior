// Punto 1: tres secciones de servidor de distinta velocidad. Punto 2: giratorio frente a esqueleto.
import { connection } from 'next/server';

const esperar = (ms: number) => new Promise((r) => setTimeout(r, ms));

export async function Resumen() { await connection(); await esperar(80); return <p>Saldo: $1.250.000</p>; }
export async function Movimientos() { await connection(); await esperar(600); return <ul>{['Compra', 'Pago', 'Retiro'].map((m) => <li key={m}>{m}</li>)}</ul>; }
export async function Recomendados() { await connection(); await esperar(1500); return <p>Te puede interesar: CDT a 90 días</p>; }

export async function BloqueAlto() {
  await connection();
  await esperar(700);
  return <div style={{ height: 320, background: 'var(--borde)' }}>Contenido de 320 px</div>;
}
export const Giratorio = () => <p style={{ height: 24, margin: 0 }} aria-busy="true">⏳</p>;
export const EsqueletoAlto = () => <div className="esqueleto" style={{ height: 320, margin: 0 }} aria-hidden="true" />;
