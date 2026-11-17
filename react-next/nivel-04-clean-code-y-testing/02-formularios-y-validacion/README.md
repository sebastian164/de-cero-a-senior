# 02 — Formularios y validación

Los criterios de experiencia de uso y de accesibilidad de un formulario no dependen del framework; están desarrollados en la [sesión de formularios de la ruta Angular](../../../angular/nivel-04-clean-code-y-testing/02-formularios-y-validacion/). Aquí, cómo se construyen en React y Next.js.

## Idea

Hay tres formas de manejar un formulario en React, de menos a más código de cliente: dejar que el navegador lo maneje y enviarlo a una acción de servidor, usar una biblioteca que registra los campos, o controlar cada campo con estado. Elegir bien evita la mitad de los renderizados y de los errores.

## Lo esencial

| Enfoque | Cómo | Conviene |
|---|---|---|
| **No controlado + acción de servidor** | El DOM guarda los valores; `<form action={accion}>` envía un `FormData` | Formularios simples en Next.js. Funciona incluso antes de que cargue JavaScript |
| **Biblioteca de formularios** (React Hook Form y similares) | Registra los campos, valida y expone errores con pocos renderizados | Formularios medianos y grandes, validación en vivo, campos dinámicos |
| **Controlado** (`value` + `onChange` con `useState`) | Cada tecla actualiza el estado | Uno o dos campos, o cuando el valor afecta a otra parte de la interfaz al escribir |

### Un esquema, dos lados

La validación debe ejecutarse en el servidor siempre, y en el cliente por experiencia de uso. Definir las reglas una vez como **esquema** (Zod, Valibot) permite usarlas en ambos lados y derivar el tipo:

```ts
// registro/esquema.ts — compartido por cliente y servidor
export const EsquemaRegistro = z.object({
  correo: z.string().email('Escribe un correo válido'),
  documento: z.string().regex(/^\d{6,10}$/, 'Entre 6 y 10 dígitos'),
  clave: z.string().min(12, 'Mínimo 12 caracteres'),
  confirmacion: z.string(),
}).refine((d) => d.clave === d.confirmacion, {
  path: ['confirmacion'],
  message: 'Las claves no coinciden',
});

export type DatosRegistro = z.infer<typeof EsquemaRegistro>;
```

## Ejemplo: acción de servidor con estado

```ts
// registro/acciones.ts
'use server';

export interface EstadoRegistro {
  errores?: Partial<Record<keyof DatosRegistro, string[]>>;
  mensaje?: string;
  valores?: Partial<DatosRegistro>;
}

export async function registrar(_previo: EstadoRegistro, datos: FormData): Promise<EstadoRegistro> {
  const entrada = Object.fromEntries(datos);
  const resultado = EsquemaRegistro.safeParse(entrada);            // el servidor valida SIEMPRE

  if (!resultado.success) {
    return { errores: resultado.error.flatten().fieldErrors, valores: entrada as Partial<DatosRegistro> };
  }
  if (await existeCorreo(resultado.data.correo)) {
    return { errores: { correo: ['Ese correo ya está registrado'] }, valores: resultado.data };
  }
  await crearUsuario(resultado.data);
  redirect('/bienvenida');
}
```

```tsx
// registro/formulario.tsx
'use client';

export function FormularioRegistro() {
  const [estado, accion, pendiente] = useActionState(registrar, {});

  return (
    <form action={accion} noValidate>
      <Campo name="correo" type="email" etiqueta="Correo" autoComplete="email"
             defaultValue={estado.valores?.correo} error={estado.errores?.correo?.[0]} />
      <Campo name="documento" etiqueta="Documento" inputMode="numeric"
             defaultValue={estado.valores?.documento} error={estado.errores?.documento?.[0]} />
      <Campo name="clave" type="password" etiqueta="Clave" autoComplete="new-password"
             error={estado.errores?.clave?.[0]} />
      <Campo name="confirmacion" type="password" etiqueta="Confirma la clave" autoComplete="new-password"
             error={estado.errores?.confirmacion?.[0]} />

      {estado.mensaje && <p role="alert">{estado.mensaje}</p>}
      <button type="submit" disabled={pendiente}>{pendiente ? 'Registrando…' : 'Registrarme'}</button>
    </form>
  );
}
```

Los valores se devuelven al formulario para que el usuario no pierda lo escrito, y el botón se deshabilita mientras hay un envío en curso. `Campo` es el componente de la sesión de LSP: etiqueta asociada, `aria-invalid` y error anunciado.

## Con biblioteca de formularios

Cuando hace falta validar mientras se escribe, campos dinámicos o dependencias entre campos:

```tsx
// registro/formulario-en-vivo.tsx
'use client';

export function FormularioRegistroEnVivo() {
  const { register, handleSubmit, formState: { errors, isSubmitting }, setError } =
    useForm<DatosRegistro>({ resolver: zodResolver(EsquemaRegistro), mode: 'onBlur' });

  const enviar = handleSubmit(async (datos) => {
    const resultado = await registrarEnServidor(datos);
    if (resultado.errores) {
      Object.entries(resultado.errores).forEach(([campo, mensajes]) =>
        setError(campo as keyof DatosRegistro, { message: mensajes?.[0] }));     // errores del servidor al campo
    }
  });

  return (
    <form onSubmit={enviar} noValidate>
      <Campo etiqueta="Correo" type="email" autoComplete="email" {...register('correo')} error={errors.correo?.message} />
      <Campo etiqueta="Documento" inputMode="numeric" {...register('documento')} error={errors.documento?.message} />
      <Campo etiqueta="Clave" type="password" autoComplete="new-password" {...register('clave')} error={errors.clave?.message} />
      <Campo etiqueta="Confirma la clave" type="password" autoComplete="new-password"
             {...register('confirmacion')} error={errors.confirmacion?.message} />
      <button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Registrando…' : 'Registrarme'}</button>
    </form>
  );
}
```

Con `mode: 'onBlur'` cada campo se valida al salir de él, con el mismo esquema. La versión con acción de servidor, en cambio, solo valida al enviar. `registrarEnServidor` es una acción de servidor que devuelve el mismo `EstadoRegistro` que `registrar`, sin redirigir.

Una advertencia para Next.js 16 con `cacheComponents: true` (comprobada con 16.4.6): si en la misma página conviven un formulario de biblioteca con lista dinámica de campos (`useFieldArray`) y uno de acción de servidor, el prerender de la página se aborta y el formulario de acción de servidor deja de salir en el HTML (sin JavaScript no habría nada que enviar). Carga el formulario de biblioteca solo en el navegador (`next/dynamic` con `ssr: false`); necesita JavaScript de todos modos. Y no evalúes `new Date()` al cargar el módulo del esquema (`.max(new Date())`): queda congelado en el primer arranque; compara dentro de un `refine`.

## La regla que no cambia

La validación del cliente es experiencia de usuario. **Toda acción de servidor y todo endpoint valida y autoriza de nuevo**, porque se pueden invocar directamente sin pasar por el formulario.

## Errores comunes

- Validar solo en el cliente.
- Reglas distintas en cliente y servidor.
- Controlar con estado formularios grandes: un renderizado por tecla en todo el formulario.
- Acciones de servidor sin validación ni autorización.
- Perder lo escrito ante un error del servidor.
- Sin protección contra doble envío.
- Errores sin asociar al campo, o solo en color rojo.
- Mostrar errores antes de que el usuario termine de escribir.
- Confiar en los campos ocultos (`hidden`) para datos sensibles: se pueden modificar.

## Taller

Construye el registro de una clínica:

1. Esquema compartido con validaciones de campo y entre campos.
2. Versión con acción de servidor y `useActionState`; comprueba que funciona con JavaScript desactivado.
3. Versión con biblioteca de formularios y validación al salir del campo.
4. Lista dinámica de contactos de emergencia (mínimo uno, máximo tres).
5. Errores del servidor asignados a su campo; foco en el primer error.
6. Invoca la acción de servidor directamente con datos inválidos (fuera del formulario) y verifica que los rechaza.
7. Prueba el esquema sin React.

Rama `taller/rx-n4-02-formularios`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. Controlado, no controlado o biblioteca: criterios.
2. ¿Por qué compartir un esquema entre cliente y servidor?
3. ¿Qué es la mejora progresiva y cómo la habilitan las acciones de servidor?
4. ¿Qué debe hacer siempre una acción de servidor antes de modificar datos?
