import { Suspense } from 'react';
import { connection } from 'next/server';
import { UrlEnElCliente } from './url-cliente';

async function Servidor() {
  await connection();
  return <p>Variable de servidor leída en ejecución: <span data-testid="servidor">{process.env.SECRETO_DEL_SERVIDOR ? 'definida (no se muestra)' : 'no definida'}</span></p>;
}
export default function Pagina() {
  return (
    <main>
      <h1>Configuración pública y privada</h1>
      <p>NEXT_PUBLIC_ incrustada al construir: <span data-testid="publica">{process.env.NEXT_PUBLIC_URL_API}</span></p>
      <UrlEnElCliente />
      <Suspense fallback={<p>…</p>}><Servidor /></Suspense>
    </main>
  );
}
