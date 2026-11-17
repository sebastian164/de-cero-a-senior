import { Bloque } from '@app/bloque';
import { Buscador } from '@/react-next/n1-02-hooks-y-efectos/buscador';
import {
  BuscadorConCarrera, BuscadorSinCarrera, Carrito, DemoHooks, EditorClientes,
} from '@talleres/react-next/nivel-01-fundamentos/02-hooks-y-efectos/componentes';

export const metadata = { title: 'nivel-01/02 · Hooks y efectos' };

export default function Pagina() {
  return (
    <main>
      <h1>nivel-01/02 · Hooks y efectos</h1>
      <Bloque titulo="Ejemplo “Después” del README: Buscador" origen="proyectos/react-next/n1-02-hooks-y-efectos/buscador.tsx">
        <Buscador categoria="bebidas" />
      </Bloque>
      <Bloque titulo="Taller 1 y 2: useAlmacenLocal y useEnLinea" origen="talleres/…/02-hooks-y-efectos/hooks.ts">
        <DemoHooks />
      </Bloque>
      <Bloque titulo="Taller 3: sin efectos para derivar ni para avisar al padre" origen="talleres/…/componentes.tsx">
        <Carrito />
      </Bloque>
      <Bloque titulo="Taller 4: carrera entre peticiones" origen="talleres/…/componentes.tsx">
        <p>Escribe “caf” rápido en los dos campos: el primero termina mostrando los resultados de “c”.</p>
        <BuscadorConCarrera />
        <BuscadorSinCarrera />
      </Bloque>
      <Bloque titulo="Taller 5: reiniciar con key" origen="talleres/…/componentes.tsx">
        <EditorClientes />
      </Bloque>
    </main>
  );
}
