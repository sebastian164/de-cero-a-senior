// Sin transmisión: la ruta espera a la sección lenta antes de enviar nada (se permite expresamente con instant = false).
import { Todo } from '../../lenta-comun';

export const instant = false;
export default async function Pagina() { return <main><Todo /></main>; }
