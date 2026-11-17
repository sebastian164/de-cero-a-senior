// Contexto: avisar(), que el ejemplo de interfaz optimista usa para mostrar el error devuelto por la acción.
export const avisos = new EventTarget();
export function avisar(mensaje: string) {
  avisos.dispatchEvent(new CustomEvent('aviso', { detail: mensaje }));
}
