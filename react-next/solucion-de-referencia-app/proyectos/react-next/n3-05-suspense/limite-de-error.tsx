'use client';
// ---- react-next/nivel-03-patrones/05-suspense-limites-de-error-y-patrones-heredados/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
import { Suspense } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import { reportar } from './reporte';
import { EsqueletoTabla, PedidosDelClienteCliente as PedidosDelCliente } from './secciones-cliente';

export function SeccionPedidos() {
  return (
// Con la biblioteca react-error-boundary
<ErrorBoundary
  FallbackComponent={({ error, resetErrorBoundary }) => (
    <div role="alert">
      <p>No pudimos cargar los pedidos.</p>
      <button type="button" onClick={resetErrorBoundary}>Reintentar</button>
    </div>
  )}
  onError={(error) => reportar(error)}
>
  <Suspense fallback={<EsqueletoTabla />}>
    <PedidosDelCliente />
  </Suspense>
</ErrorBoundary>
  );
}
