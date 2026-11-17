// Contexto: implementaciones reales de los puertos de la sesión DIP (navegador y analítica).
import type { AlmacenDeCarrito, Analitica, LineaCarrito } from './carrito';

export const almacenLocal: AlmacenDeCarrito = {
  leer: () => JSON.parse(localStorage.getItem('carrito-dip') ?? '[]') as LineaCarrito[],
  guardar: (lineas) => localStorage.setItem('carrito-dip', JSON.stringify(lineas)),
};

declare global { interface Window { gtag?: (...args: unknown[]) => void; eventosAnalitica?: unknown[] } }

export const analiticaGoogle: Analitica = {
  productoAgregado: (productoId) => {
    window.gtag?.('event', 'add_to_cart', { id: productoId });
    (window.eventosAnalitica ??= []).push({ evento: 'add_to_cart', productoId });
  },
};
