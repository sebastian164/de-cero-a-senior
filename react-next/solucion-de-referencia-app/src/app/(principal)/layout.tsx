import type { Metadata } from 'next';
import Link from 'next/link';
import { Proveedores } from '@app/proveedores';
import { IndicadorCarrito } from '@/react-next/n5-01-servidor-cliente/acciones-carrito';
import '../globals.css';

export const metadata: Metadata = {
  title: { default: 'Referencia React y Next.js', template: '%s · Referencia' },
  description: 'Ejemplos y soluciones de los talleres de la ruta React y Next.js, ejecutándose.',
};

export default function DisenoRaiz({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es">
      <body>
        <Proveedores>
          <header className="barra">
            <Link href="/">Índice</Link>
            <span className="suave">Curso “De cero a senior” · ruta React y Next.js</span>
            <IndicadorCarrito />
          </header>
          {children}
        </Proveedores>
      </body>
    </html>
  );
}
