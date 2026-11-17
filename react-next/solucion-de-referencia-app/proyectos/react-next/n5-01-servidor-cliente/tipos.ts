// Contexto del ejemplo "Antes" (nivel-05/01): las formas que el componente de cliente espera de los endpoints.
export interface Producto { readonly id: string; readonly nombre: string; readonly precio: number; readonly descripcion: string; readonly imagenes: readonly string[] }
export interface Resena { readonly id: string; readonly autor: string; readonly texto: string; readonly estrellas: number }
