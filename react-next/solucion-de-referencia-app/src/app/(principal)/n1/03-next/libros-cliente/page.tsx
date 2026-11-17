'use client';
// Punto 5: toda la página marcada como cliente (misma lista y mismo formulario que /libros), con los datos traídos desde el navegador.
import { ListaLibrosCliente } from '@talleres/react-next/nivel-01-fundamentos/03-fundamentos-de-next/lista-cliente';
import { FormularioLibro } from '@talleres/react-next/nivel-01-fundamentos/03-fundamentos-de-next/formulario-libro';

export default function PaginaLibrosCliente() {
  return (
    <main>
      <h1>Libros (todo en el cliente)</h1>
      <ListaLibrosCliente />
      <h2>Nuevo libro</h2>
      <FormularioLibro />
    </main>
  );
}
