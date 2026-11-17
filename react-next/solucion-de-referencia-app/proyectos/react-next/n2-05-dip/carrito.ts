// ---- react-next/nivel-02-solid/05-dip/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
export interface Producto { readonly id: string; readonly nombre: string; readonly precio: number }
export interface LineaCarrito extends Producto { readonly agregadoEn: number }

// carrito/puertos.ts — contratos definidos por la lógica
export interface AlmacenDeCarrito {
  leer(): LineaCarrito[];
  guardar(lineas: LineaCarrito[]): void;
}
export interface Analitica {
  productoAgregado(productoId: string): void;
}
export interface Dependencias {
  almacen: AlmacenDeCarrito;
  analitica: Analitica;
  ahora: () => number;
}

// carrito/agregar.ts — lógica pura respecto a sus dependencias
export function agregarAlCarrito(producto: Producto, { almacen, analitica, ahora }: Dependencias): LineaCarrito[] {
  const lineas = [...almacen.leer(), { ...producto, agregadoEn: ahora() }];
  almacen.guardar(lineas);
  analitica.productoAgregado(producto.id);
  return lineas;
}
