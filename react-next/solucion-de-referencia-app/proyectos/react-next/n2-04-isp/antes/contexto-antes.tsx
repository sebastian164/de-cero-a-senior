import { createContext, useContext } from 'react';
// Apoyo (no está en el README): los tipos y el contador de renderizados.
interface Usuario { nombres: string; apellidos: string; perfil: { fotoUrl: string } }
interface Carrito { items: string[] }
type Tema = 'claro' | 'oscuro';
interface Aviso { texto: string }
interface Producto { id: string }
export const conteo = { interruptor: 0 };

function Avatar({ usuario }: { usuario: Usuario }) {       // 40 campos
  return <img src={usuario.perfil.fotoUrl} alt={`${usuario.nombres} ${usuario.apellidos}`} />;
}

const ContextoApp = createContext<{
  usuario: Usuario; carrito: Carrito; tema: Tema; avisos: Aviso[];
  setTema: (t: Tema) => void; agregarAlCarrito: (p: Producto) => void; /* ... */
} | null>(null);

function InterruptorDeTema() {
  conteo.interruptor++;
  const { tema, setTema } = useContext(ContextoApp)!;      // se vuelve a renderizar cuando cambia el carrito
  return <button onClick={() => setTema(tema === 'claro' ? 'oscuro' : 'claro')}>{tema}</button>;
}

export { Avatar, ContextoApp, InterruptorDeTema, type Carrito, type Tema, type Usuario };
