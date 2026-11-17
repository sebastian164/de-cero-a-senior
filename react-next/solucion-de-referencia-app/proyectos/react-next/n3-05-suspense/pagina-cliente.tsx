// ---- react-next/nivel-03-patrones/05-suspense-limites-de-error-y-patrones-heredados/README.md (bloque 1), copiado tal cual entre el contexto agregado ----
import { Suspense } from 'react';
import { EncabezadoCliente, EsqueletoEncabezado, EsqueletoTabla, PedidosDelCliente } from './secciones';

export default function PaginaCliente({ params }: { params: Promise<{ id: string }> }) {
  return (
    <main>
      <Suspense fallback={<EsqueletoEncabezado />}>
        <EncabezadoCliente params={params} />          {/* rápido */}
      </Suspense>
      <Suspense fallback={<EsqueletoTabla />}>
        <PedidosDelCliente params={params} />          {/* lento: no bloquea al resto */}
      </Suspense>
    </main>
  );
}
