class Carrito {
  items: { producto: string; precio: number; cantidad: number }[] = [];
  total = 0;
  pagado = false;
}

const carrito = new Carrito();
carrito.items.push({ producto: 'Café', precio: 18000, cantidad: -3 });
carrito.total = 1;
carrito.pagado = true;        // sin ítems válidos y con un total inventado

export { Carrito, carrito };
