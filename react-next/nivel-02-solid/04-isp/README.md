# 04 — ISP en React

Explicación completa: [ISP en la ruta Java](../../../java/nivel-02-solid/04-isp/).

## Idea

Un componente debería recibir lo que usa y nada más. Pasarle el objeto completo "por si acaso" lo ata a datos que no necesita, lo hace difícil de reutilizar y de probar, y en React además lo hace volver a renderizarse por cambios que no le importan.

## Dónde se aplica

| Contrato | Violación típica | Corrección |
|---|---|---|
| Propiedades | `<Avatar usuario={usuario} />` para mostrar una foto | `<Avatar src={…} alt={…} />` |
| Contexto | Un contexto global con todo el estado; cualquier cambio renderiza a todos los consumidores | Contextos pequeños por tema; separar estado de acciones; selectores |
| Hooks | `useUsuario()` que devuelve veinte cosas | Hooks enfocados: `usePermisos()`, `usePreferencias()` |
| Datos del servidor | Enviar el registro entero a un componente de cliente | Enviar solo los campos necesarios |

El último punto tiene además consecuencia de seguridad en Next.js: **todo lo que se pasa a un componente de cliente viaja al navegador.** Pasar el usuario completo puede exponer campos que no debían salir del servidor.

## Antes

```tsx
function Avatar({ usuario }: { usuario: Usuario }) {       // 40 campos
  return <img src={usuario.perfil.fotoUrl} alt={`${usuario.nombres} ${usuario.apellidos}`} />;
}

const ContextoApp = createContext<{
  usuario: Usuario; carrito: Carrito; tema: Tema; avisos: Aviso[];
  setTema: (t: Tema) => void; agregarAlCarrito: (p: Producto) => void; /* ... */
} | null>(null);

function InterruptorDeTema() {
  const { tema, setTema } = useContext(ContextoApp)!;      // se vuelve a renderizar cuando cambia el carrito
  return <button onClick={() => setTema(tema === 'claro' ? 'oscuro' : 'claro')}>{tema}</button>;
}
```

## Después

```tsx
function Avatar({ src, alt, tamano = 40 }: { src: string; alt: string; tamano?: number }) {
  return <img src={src} alt={alt} width={tamano} height={tamano} />;
}

// Un contexto por tema, y estado separado de acciones
const ContextoTema = createContext<Tema>('claro');
const ContextoCambiarTema = createContext<(tema: Tema) => void>(() => {});

export function ProveedorDeTema({ children }: { children: React.ReactNode }) {
  const [tema, setTema] = useState<Tema>('claro');
  return (
    <ContextoCambiarTema value={setTema}>
      <ContextoTema value={tema}>{children}</ContextoTema>
    </ContextoCambiarTema>
  );
}

export const useTema = () => useContext(ContextoTema);
export const useCambiarTema = () => useContext(ContextoCambiarTema);
```

En Next.js, proyectar en el servidor antes de cruzar al cliente:

```tsx
// Componente de servidor
export default async function Encabezado() {
  const usuario = await obtenerUsuarioActual();            // incluye correo, documento, roles…
  return <MenuDeUsuario nombre={usuario.nombres} fotoUrl={usuario.perfil.fotoUrl} />;   // solo dos campos cruzan
}
```

Tipos estrechos derivados del modelo:

```ts
type ResumenUsuario = Pick<Usuario, 'id' | 'nombres'> & { fotoUrl: string };
```

## Cuándo no

Si el componente usa casi todo el objeto, recibirlo entero es más claro que quince propiedades. Si varias propiedades viajan siempre juntas, agrúpalas en un tipo.

## Errores comunes

- Objetos de dominio completos en componentes de presentación.
- Un contexto global con todo.
- Hooks que devuelven un objeto enorme.
- Pasar a componentes de cliente registros completos traídos de la base de datos.
- Decenas de propiedades sueltas que siempre van juntas.
- Propagar con `{...props}` objetos enteros sin saber qué contienen.

## Taller

1. Reduce las propiedades de tres componentes de presentación a lo que usan.
2. Divide un contexto grande en contextos pequeños y comprueba con las herramientas de React que los renderizados innecesarios desaparecieron.
3. En una aplicación Next.js, revisa qué datos cruzan del servidor al cliente (mira la respuesta en la pestaña de red) y recorta.
4. Señala un caso donde decidiste pasar el objeto completo.

Rama `taller/rx-n2-04-isp`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué gana un componente al recibir solo lo que usa?
2. ¿Por qué un contexto grande afecta al rendimiento y cómo se divide?
3. ¿Qué riesgo tiene pasar objetos completos del servidor al cliente?
