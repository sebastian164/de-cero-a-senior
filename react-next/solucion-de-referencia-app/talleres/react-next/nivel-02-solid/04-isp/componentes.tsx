// Punto 1: tres componentes de presentación reducidos a lo que usan.
export interface Usuario {
  id: string; nombres: string; apellidos: string; correo: string; documento: string; roles: string[];
  perfil: { fotoUrl: string; biografia: string };
}
export interface Producto { sku: string; nombre: string; precio: number; existencias: number; proveedor: { nit: string; nombre: string }; costo: number }

// --- Antes: reciben el objeto completo ---
export function AvatarAntes({ usuario }: { usuario: Usuario }) {
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={usuario.perfil.fotoUrl} alt={`${usuario.nombres} ${usuario.apellidos}`} width={40} height={40} />;
}
export function PrecioAntes({ producto }: { producto: Producto }) {
  return <span>{producto.nombre}: {producto.precio}</span>;
}
export function InsigniaRolAntes({ usuario }: { usuario: Usuario }) {
  return <span>{usuario.roles.includes('admin') ? 'Administrador' : 'Usuario'}</span>;
}

// --- Después: solo lo que usan ---
export function Precio({ nombre, precio }: { nombre: string; precio: number }) {
  return <span>{nombre}: {precio}</span>;
}
export function InsigniaRol({ esAdministrador }: { esAdministrador: boolean }) {
  return <span>{esAdministrador ? 'Administrador' : 'Usuario'}</span>;
}
// (Avatar reducido es el del README: proyectos/react-next/n2-04-isp/tema.tsx)

// --- Un caso donde pasar el objeto completo es lo correcto (punto 4) ---
// La fila muestra cuatro de los campos y además ENTREGA el producto entero a quien lo elige: dividirlo en propiedades sueltas
// obligaría a reconstruirlo para onElegir y a cambiar la firma cada vez que el producto gane un campo.
export function FilaProducto({ producto, onElegir }: { producto: Producto; onElegir: (producto: Producto) => void }) {
  return (
    <li>
      <button type="button" onClick={() => onElegir(producto)}>{producto.nombre}</button> {producto.sku} · {producto.precio} · {producto.existencias} en stock
    </li>
  );
}
