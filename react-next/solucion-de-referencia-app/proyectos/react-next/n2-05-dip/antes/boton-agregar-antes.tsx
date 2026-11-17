'use client';
// Apoyo (no está en el README): el tipo del producto.
interface Producto { id: string }
function BotonAgregar({ producto }: { producto: Producto }) {
  function agregar() {
    const carrito = JSON.parse(localStorage.getItem('carrito') ?? '[]');     // navegador
    carrito.push({ ...producto, agregadoEn: Date.now() });                   // reloj
    localStorage.setItem('carrito', JSON.stringify(carrito));
    (window as any).gtag('event', 'add_to_cart', { id: producto.id });       // SDK de un tercero
    fetch('/api/carrito', { method: 'POST', body: JSON.stringify(producto) });
  }
  return <button onClick={agregar}>Agregar</button>;
}

export { BotonAgregar };
