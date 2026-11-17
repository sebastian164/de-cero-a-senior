// Punto 5: taladro de propiedades resuelto con composición, sin contexto.
interface Usuario { nombre: string }

// Antes: Pagina -> Diseno -> Barra -> Avatar, y solo Avatar usa el usuario.
export function PaginaConTaladro({ usuario }: { usuario: Usuario }) {
  return <DisenoTaladro usuario={usuario} />;
}
function DisenoTaladro({ usuario }: { usuario: Usuario }) {
  return <div><BarraTaladro usuario={usuario} /><p>Contenido</p></div>;
}
function BarraTaladro({ usuario }: { usuario: Usuario }) {
  return <nav><AvatarUsuario usuario={usuario} /></nav>;
}

// Después: la página arma el Avatar y lo pasa como hueco; Diseno y Barra no conocen al usuario.
export function PaginaCompuesta({ usuario }: { usuario: Usuario }) {
  return <Diseno barra={<Barra><AvatarUsuario usuario={usuario} /></Barra>}><p>Contenido</p></Diseno>;
}
function Diseno({ barra, children }: { barra: React.ReactNode; children: React.ReactNode }) {
  return <div>{barra}{children}</div>;
}
function Barra({ children }: { children: React.ReactNode }) {
  return <nav>{children}</nav>;
}

function AvatarUsuario({ usuario }: { usuario: Usuario }) {
  return <span>{usuario.nombre}</span>;
}
