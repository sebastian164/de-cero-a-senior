import { Suspense } from 'react';
import Link from 'next/link';
import { Bloque } from '@app/bloque';
import { Movimientos, Recomendados, Resumen } from '@talleres/react-next/nivel-03-patrones/05-suspense-limites-de-error-y-patrones-heredados/secciones-servidor';
import { DemoErrores } from '@talleres/react-next/nivel-03-patrones/05-suspense-limites-de-error-y-patrones-heredados/demo-errores';

export const metadata = { title: 'nivel-03/05 · Suspense y errores' };

const Esq = ({ alto }: { alto: number }) => <div className="esqueleto" style={{ height: alto }} aria-hidden="true" />;

export default function Pagina() {
  return (
    <main>
      <h1>nivel-03/05 · Suspense, límites de error y patrones heredados</h1>
      <p><Link href="/n3/05-suspense/clientes/42">Ejemplo del README: PaginaCliente con dos límites</Link> · <Link href="/n3/05-suspense/cls?modo=giratorio">CLS con giratorio</Link> · <Link href="/n3/05-suspense/cls?modo=esqueleto">CLS con esqueleto</Link></p>
      <Bloque titulo="Taller 1: tres secciones de distinta velocidad" origen="talleres/…/secciones-servidor.tsx">
        {/* Resumen (80 ms) y movimientos (600 ms) aparecen juntos: se leen juntos. Recomendados (1,5 s) va aparte: es lo menos importante. */}
        <Suspense fallback={<Esq alto={120} />}><Resumen /><Movimientos /></Suspense>
        <Suspense fallback={<Esq alto={24} />}><Recomendados /></Suspense>
      </Bloque>
      <Bloque titulo="Talleres 3 y 4: límites por sección y errores en manejadores" origen="talleres/…/demo-errores.tsx">
        <DemoErrores />
      </Bloque>
    </main>
  );
}
