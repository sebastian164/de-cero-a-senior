import { Bloque } from '@app/bloque';
import { ProveedorDeCarrito } from '@talleres/react-next/nivel-03-patrones/03-contexto-reductores-y-estado/carrito';
import { BotonAgregar, CampoCupon, ResumenCarrito } from '@talleres/react-next/nivel-03-patrones/03-contexto-reductores-y-estado/componentes';
import { ContadorCarrito } from '@/react-next/n3-03-carrito/almacen';

export const metadata = { title: 'nivel-03/03 · Carrito' };

export default function Pagina() {
  return (
    <main>
      <h1>nivel-03/03 · Contexto, reductores y estado compartido</h1>
      <Bloque titulo="Taller: carrito con reductor, dos contextos, persistencia y cupones" origen="talleres/…/03-contexto-reductores-y-estado/">
        <ProveedorDeCarrito>
          <BotonAgregar producto={{ id: 'cafe', nombre: 'Café', precio: 18000 }} />{' '}
          <BotonAgregar producto={{ id: 'pan', nombre: 'Pan', precio: 3500 }} />
          <ResumenCarrito />
          <CampoCupon />
        </ProveedorDeCarrito>
      </Bloque>
      <Bloque titulo="Ejemplo del README: almacén externo con selector" origen="proyectos/react-next/n3-03-carrito/almacen.tsx">
        <p>Líneas en el almacén Zustand: <ContadorCarrito /></p>
      </Bloque>
    </main>
  );
}
