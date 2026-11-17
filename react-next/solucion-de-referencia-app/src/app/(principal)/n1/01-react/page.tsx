import { Bloque } from '@app/bloque';
import { PaginaPedidos } from '@/react-next/n1-01-fundamentos-de-react/pedidos';
import { PaginaTareas } from '@talleres/react-next/nivel-01-fundamentos/01-fundamentos-de-react/lista-tareas';
import { ListaConNotas } from '@talleres/react-next/nivel-01-fundamentos/01-fundamentos-de-react/clave-indice';

export const metadata = { title: 'nivel-01/01 · Fundamentos de React' };

export default function Pagina() {
  return (
    <main>
      <h1>nivel-01/01 · Fundamentos de React</h1>
      <Bloque titulo="Ejemplo “Después” del README" origen="proyectos/react-next/n1-01-fundamentos-de-react/pedidos.tsx">
        <PaginaPedidos />
      </Bloque>
      <Bloque titulo="Taller: lista de tareas" origen="talleres/react-next/nivel-01-fundamentos/01-fundamentos-de-react/lista-tareas.tsx">
        <PaginaTareas iniciales={[{ id: 't1', titulo: 'Leer el README', hecha: true }, { id: 't2', titulo: 'Hacer el taller', hecha: false }]} />
      </Bloque>
      <Bloque titulo="Taller, punto 5: el índice como clave" origen="talleres/react-next/nivel-01-fundamentos/01-fundamentos-de-react/clave-indice.tsx">
        <p>Escribe una nota para Ana en las dos listas y luego quita a Ana: con el índice como clave, la nota pasa a Luis.</p>
        <ListaConNotas claveIndice />
        <ListaConNotas claveIndice={false} />
      </Bloque>
    </main>
  );
}
