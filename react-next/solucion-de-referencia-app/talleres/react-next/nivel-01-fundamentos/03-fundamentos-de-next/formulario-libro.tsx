'use client';
// La única parte interactiva de la lista: el formulario. Funciona también sin JavaScript (action de servidor).
import { useActionState } from 'react';
import { crearLibro, type EstadoCrearLibro } from './acciones';

export function FormularioLibro() {
  const [estado, accion, pendiente] = useActionState<EstadoCrearLibro, FormData>(crearLibro, {});
  return (
    <form action={accion}>
      <p><label>Título <input name="titulo" required /></label> {estado.errores?.titulo && <span role="alert">{estado.errores.titulo[0]}</span>}</p>
      <p><label>Autor <input name="autor" required /></label> {estado.errores?.autor && <span role="alert">{estado.errores.autor[0]}</span>}</p>
      <p><label>Año <input name="anio" type="number" required /></label> {estado.errores?.anio && <span role="alert">{estado.errores.anio[0]}</span>}</p>
      <button type="submit" disabled={pendiente}>{pendiente ? 'Guardando…' : 'Crear libro'}</button>
      {estado.creado && <p role="status">Creado: {estado.creado}</p>}
    </form>
  );
}
