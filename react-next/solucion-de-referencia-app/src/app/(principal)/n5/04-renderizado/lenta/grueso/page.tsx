// Una sola frontera envuelve todo: la cáscara llega al instante pero el contenido útil espera a la sección lenta.
import { Envuelto, Todo } from '../../lenta-comun';

export default function Pagina() { return <main><Envuelto fallback="Cargando todo…"><Todo /></Envuelto></main>; }
