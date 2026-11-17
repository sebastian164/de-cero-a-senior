import { FichaDeCliente } from '@app/demos-n5';
import { PRODUCTOS } from '@/react-next/n5-01-servidor-cliente/contexto';

export const metadata = { title: 'Fuga al cliente' };

export default function Pagina() {
  return <main><h1>Registro completo hacia el cliente</h1><FichaDeCliente producto={PRODUCTOS[0]!} /></main>;
}
