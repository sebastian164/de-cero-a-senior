import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

/** Punto 6: título, descripción, vista previa para redes y datos estructurados (JSON-LD) por página. */
@Injectable({ providedIn: 'root' })
export class Seo {
  private readonly titulo = inject(Title);
  private readonly meta = inject(Meta);
  private readonly doc = inject(DOCUMENT);

  fijar(titulo: string, descripcion: string, datosEstructurados?: object): void {
    this.titulo.setTitle(titulo);
    this.meta.updateTag({ name: 'description', content: descripcion });
    this.meta.updateTag({ property: 'og:title', content: titulo });
    this.meta.updateTag({ property: 'og:description', content: descripcion });
    this.doc.head.querySelector('script[data-ld]')?.remove();
    if (datosEstructurados) {
      const s = this.doc.createElement('script');
      s.type = 'application/ld+json';
      s.setAttribute('data-ld', '');
      s.textContent = JSON.stringify(datosEstructurados);
      this.doc.head.appendChild(s);
    }
  }
}
