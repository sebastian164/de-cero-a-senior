import { createEnvironmentInjector, EnvironmentInjector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { proveedoresEnMemoria, Recordatorios } from './recordatorios';

describe('4. proveedores por ruta', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient()] }));

  it('con los contratos solo en la ruta, el Recordatorios de root no los encuentra', () => {
    const ruta = createEnvironmentInjector(proveedoresEnMemoria, TestBed.inject(EnvironmentInjector));
    expect(() => ruta.get(Recordatorios)).toThrow(/NG0201|No provider/);
  });

  it('proveyendo también Recordatorios en la ruta, funciona', () => {
    const ruta = createEnvironmentInjector([...proveedoresEnMemoria, Recordatorios], TestBed.inject(EnvironmentInjector));
    expect(ruta.get(Recordatorios)).toBeInstanceOf(Recordatorios);
  });
});
