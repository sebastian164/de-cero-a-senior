import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { forkJoin, map, Observable } from 'rxjs';

/**
 * 1. Adaptadores de dos entidades con la forma de una API pública conocida (JSONPlaceholder: /users y /posts).
 *    El servidor simulado sirve esa misma forma, para no depender de internet al probar.
 *    Este es el ÚNICO archivo que conoce los nombres de campo del servidor (lo comprueba adaptadores.spec.ts).
 */
interface UsuarioDto {
  id: number;
  name: string;
  username: string;
  email: string;
  address: { city: string };
  company: { name: string };
}
interface PublicacionDto { userId: number; id: number; title: string; body: string; }

export interface Autor { readonly id: string; readonly nombre: string; readonly correo: string; readonly ciudad: string; readonly empresa: string; }
export interface Publicacion { readonly id: string; readonly autorId: string; readonly titulo: string; readonly resumen: string; }

const exigir = (condicion: boolean, campo: string) => { if (!condicion) throw new Error(`Respuesta inválida: falta ${campo}`); };

export const aAutor = (dto: UsuarioDto): Autor => {
  exigir(typeof dto.id === 'number', 'id');
  return { id: String(dto.id), nombre: dto.name, correo: dto.email.toLowerCase(), ciudad: dto.address.city, empresa: dto.company.name };
};

export const aPublicacion = (dto: PublicacionDto): Publicacion => {
  exigir(typeof dto.userId === 'number', 'userId');
  return { id: String(dto.id), autorId: String(dto.userId), titulo: dto.title, resumen: dto.body.split('\n')[0] };
};

@Injectable({ providedIn: 'root' })
export class BlogApi {
  private readonly http = inject(HttpClient);
  autores(): Observable<Autor[]> { return this.http.get<UsuarioDto[]>('/api/users').pipe(map((d) => d.map(aAutor))); }
  publicaciones(): Observable<Publicacion[]> { return this.http.get<PublicacionDto[]>('/api/posts').pipe(map((d) => d.map(aPublicacion))); }
  todo() { return forkJoin({ autores: this.autores(), publicaciones: this.publicaciones() }); }
}
