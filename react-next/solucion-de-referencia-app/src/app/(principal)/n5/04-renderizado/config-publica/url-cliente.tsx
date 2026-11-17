'use client';
// Un componente de cliente que lee NEXT_PUBLIC_*: el valor se sustituye al construir y queda dentro del JavaScript del navegador.
export function UrlEnElCliente() {
  return <p>Leída en el navegador: <span data-testid="publica-cliente">{process.env.NEXT_PUBLIC_URL_API}</span></p>;
}
