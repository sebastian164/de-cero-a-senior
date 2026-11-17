// Diccionarios por idioma, cargados en el servidor: al navegador solo viajan los textos que un componente de cliente usa.
export const IDIOMAS = ['es', 'en'] as const;
export type Idioma = (typeof IDIOMAS)[number];
export const esIdioma = (v: string): v is Idioma => (IDIOMAS as readonly string[]).includes(v);

const es = {
  titulo: 'Productos', ficha: 'Café de Huila', precioEtiqueta: 'Precio', fechaEtiqueta: 'Disponible desde',
  cantidad: { zero: 'No hay artículos en el carrito', one: '{n} artículo en el carrito', other: '{n} artículos en el carrito' },
  agregar: 'Agregar', quitar: 'Quitar', cambiar: 'English',
} as const;
const en = {
  titulo: 'Products', ficha: 'Huila coffee', precioEtiqueta: 'Price', fechaEtiqueta: 'Available since',
  cantidad: { zero: 'No items in the cart', one: '{n} item in the cart', other: '{n} items in the cart' },
  agregar: 'Add', quitar: 'Remove', cambiar: 'Español',
} as const;
const diccionarios = { es, en };
export const cargarDiccionario = async (i: Idioma) => diccionarios[i];
export const LOCALES: Record<Idioma, string> = { es: 'es-CO', en: 'en-US' };
export const MONEDAS: Record<Idioma, string> = { es: 'COP', en: 'USD' };
