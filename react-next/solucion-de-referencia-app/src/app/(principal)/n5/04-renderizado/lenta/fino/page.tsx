// La frontera envuelve solo la sección lenta: lo demás se envía de inmediato y la lenta se transmite cuando está lista.
import { Envuelto, Lento, Rapido } from '../../lenta-comun';

export default function Pagina() { return <main><Rapido /><Envuelto fallback="Cargando sección…"><Lento /></Envuelto></main>; }
