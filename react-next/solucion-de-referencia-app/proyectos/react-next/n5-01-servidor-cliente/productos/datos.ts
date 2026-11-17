// ---- react-next/nivel-05-arquitectura/01-componentes-de-servidor-y-de-cliente/README.md (bloque 2, desde '// productos/datos.ts'), copiado tal cual entre el contexto agregado ----
import { PRODUCTOS, esperar, type Producto } from '../contexto';

// productos/datos.ts
import 'server-only';
export async function obtenerProducto(id: string): Promise<Producto | null> { await esperar(30); return PRODUCTOS.find((p) => p.id === id) ?? null; }
