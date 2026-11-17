import { FichaDeCliente } from '@app/demos-n5';
import { PRODUCTOS } from '@/react-next/n5-01-servidor-cliente/contexto';

export const metadata = { title: 'Proyección en el servidor' };

export default function Pagina() {
  const { nombre } = PRODUCTOS[0]!;           // proyección: solo el campo que la ficha necesita
  return <main><h1>Proyección hacia el cliente</h1><FichaDeCliente producto={{ nombre }} /></main>;
}
