import { FilasPrueba } from '@app/n6-01/filas';

export const metadata = { title: 'Filas: virtual' };
export default function Pagina() { return <main><h1>10.000 filas: virtual</h1><FilasPrueba virtual={true} /></main>; }
