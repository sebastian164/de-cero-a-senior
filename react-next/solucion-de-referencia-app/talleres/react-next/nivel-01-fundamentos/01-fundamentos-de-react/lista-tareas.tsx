'use client';
// Puntos 1 a 4: página con estado, lista y elemento de presentación. Todo tipado, sin any.
import { useId, useState } from 'react';
import { FILTROS, agregar, alternar, contarPendientes, eliminar, filtrar, type Filtro, type Tarea } from './tareas';

export function PaginaTareas({ iniciales = [] }: { iniciales?: readonly Tarea[] }) {
  const [tareas, setTareas] = useState<readonly Tarea[]>(iniciales);
  const [filtro, setFiltro] = useState<Filtro>('todas');
  const [titulo, setTitulo] = useState('');
  const idCampo = useId();

  // Derivados: se calculan al renderizar, no son estado.
  const visibles = filtrar(tareas, filtro);
  const pendientes = contarPendientes(tareas);

  function enviar(evento: React.FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setTareas((actuales) => agregar(actuales, crypto.randomUUID(), titulo));
    setTitulo('');
  }

  return (
    <section aria-labelledby={`${idCampo}-titulo`}>
      <h2 id={`${idCampo}-titulo`}>Tareas</h2>
      <form onSubmit={enviar}>
        <label htmlFor={idCampo}>Nueva tarea</label>{' '}
        <input id={idCampo} value={titulo} onChange={(e) => setTitulo(e.target.value)} />{' '}
        <button type="submit">Agregar</button>
      </form>
      <fieldset>
        <legend>Mostrar</legend>
        {FILTROS.map((f) => (
          <label key={f}>
            <input type="radio" name={`${idCampo}-filtro`} checked={filtro === f} onChange={() => setFiltro(f)} /> {f}{' '}
          </label>
        ))}
      </fieldset>
      <p role="status">{pendientes} pendientes</p>
      <ListaTareas
        tareas={visibles}
        onAlternar={(id) => setTareas((actuales) => alternar(actuales, id))}
        onEliminar={(id) => setTareas((actuales) => eliminar(actuales, id))}
      />
    </section>
  );
}

interface ListaTareasProps {
  readonly tareas: readonly Tarea[];
  readonly onAlternar: (id: string) => void;
  readonly onEliminar: (id: string) => void;
}

export function ListaTareas({ tareas, onAlternar, onEliminar }: ListaTareasProps) {
  if (tareas.length === 0) return <p>No hay tareas.</p>;
  return (
    <ul>
      {tareas.map((t) => (
        <ElementoTarea key={t.id} tarea={t} onAlternar={onAlternar} onEliminar={onEliminar} />
      ))}
    </ul>
  );
}

interface ElementoTareaProps {
  readonly tarea: Tarea;
  readonly onAlternar: (id: string) => void;
  readonly onEliminar: (id: string) => void;
}

export function ElementoTarea({ tarea, onAlternar, onEliminar }: ElementoTareaProps) {
  return (
    <li>
      <label>
        <input type="checkbox" checked={tarea.hecha} onChange={() => onAlternar(tarea.id)} />{' '}
        {tarea.hecha ? <s>{tarea.titulo}</s> : tarea.titulo}
      </label>{' '}
      <button type="button" onClick={() => onEliminar(tarea.id)} aria-label={`Eliminar ${tarea.titulo}`}>Eliminar</button>
    </li>
  );
}
