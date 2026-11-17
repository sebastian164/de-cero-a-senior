import { Injectable, signal } from '@angular/core';
import { Observable, of } from 'rxjs';

// 2. El servicio grande de antes: 12 métodos de cinco temas distintos.
export interface UsuariosServiceAntes {
  iniciarSesion(correo: string, clave: string): Observable<boolean>;
  cerrarSesion(): void;
  usuarioActual(): Observable<{ id: string; nombre: string } | null>;
  actualizarPerfil(cambios: { nombre?: string }): Observable<void>;
  cambiarFoto(archivo: Blob): Observable<string>;
  listarUsuarios(pagina: number): Observable<string[]>;
  bloquearUsuario(id: string): Observable<void>;
  asignarRol(id: string, rol: string): Observable<void>;
  leerPreferencias(): Observable<{ tema: string }>;
  guardarPreferencias(p: { tema: string }): Observable<void>;
  contarPedidosDelUsuarioActual(): Observable<number>;
  estadisticasDeCompra(): Observable<{ mes: string; total: number }[]>;
}

// 2. Contratos estrechos, cada uno definido por sus consumidores (ver la matriz en ANALISIS.md).
export abstract class Sesion { abstract iniciar(correo: string, clave: string): Observable<boolean>; abstract cerrar(): void; abstract actual(): Observable<{ id: string; nombre: string } | null>; }
export abstract class EdicionDePerfil { abstract actualizar(cambios: { nombre?: string }): Observable<void>; abstract cambiarFoto(archivo: Blob): Observable<string>; }
export abstract class AdministracionDeUsuarios { abstract listar(pagina: number): Observable<string[]>; abstract bloquear(id: string): Observable<void>; abstract asignarRol(id: string, rol: string): Observable<void>; }
export abstract class Preferencias { abstract leer(): Observable<{ tema: string }>; abstract guardar(p: { tema: string }): Observable<void>; }
export abstract class ConteoDePedidos { abstract delUsuarioActual(): Observable<number>; }

// 3. Una sola clase cumple todos los contratos (en memoria, para el taller).
@Injectable({ providedIn: 'root' })
export class UsuariosEnMemoria implements Sesion, EdicionDePerfil, AdministracionDeUsuarios, Preferencias, ConteoDePedidos {
  private readonly usuario = signal<{ id: string; nombre: string } | null>(null);
  private tema = 'claro';
  iniciar(correo: string) { this.usuario.set({ id: 'u1', nombre: correo.split('@')[0] }); return of(true); }
  cerrar() { this.usuario.set(null); }
  actual() { return of(this.usuario()); }
  actualizar(c: { nombre?: string }) { const u = this.usuario(); if (u && c.nombre) this.usuario.set({ ...u, nombre: c.nombre }); return of(undefined); }
  cambiarFoto() { return of('foto.png'); }
  listar() { return of(['ana', 'luis']); }
  bloquear() { return of(undefined); }
  asignarRol() { return of(undefined); }
  leer() { return of({ tema: this.tema }); }
  guardar(p: { tema: string }) { this.tema = p.tema; return of(undefined); }
  delUsuarioActual() { return of(this.usuario() ? 2 : 0); }
}

export const proveedoresDeUsuarios = [
  { provide: Sesion, useExisting: UsuariosEnMemoria },
  { provide: EdicionDePerfil, useExisting: UsuariosEnMemoria },
  { provide: AdministracionDeUsuarios, useExisting: UsuariosEnMemoria },
  { provide: Preferencias, useExisting: UsuariosEnMemoria },
  { provide: ConteoDePedidos, useExisting: UsuariosEnMemoria },
];
