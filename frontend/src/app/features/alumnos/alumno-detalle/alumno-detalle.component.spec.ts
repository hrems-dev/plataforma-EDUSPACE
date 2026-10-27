import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AlumnoDetalleComponent } from './alumno-detalle.component';

const alumno = {
  id: 1, codigo: 'AL2026-00001', numeroDocumento: '75991901', nombres: 'Lucía', apellidoPaterno: 'Mendoza', nombreCompleto: 'Mendoza Ríos, Lucía',
  fechaNacimiento: '2015-04-12', sexo: 'F', estado: 'ACTIVO', apoderados: []
};
const doc = (id: number, tipo: string, verificado: boolean) => ({
  id, alumnoId: 1, tipo, nombreArchivo: `${tipo}.pdf`, contentType: 'application/pdf', tamanoBytes: 2048, verificado, subidoEn: '2026-10-26T10:00:00'
});

async function montar(documentos: unknown[]) {
  TestBed.configureTestingModule({ imports: [AlumnoDetalleComponent], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
  const fixture = TestBed.createComponent(AlumnoDetalleComponent);
  fixture.componentRef.setInput('id', '1');
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  await fixture.whenStable();
  http.expectOne('/api/v1/alumnos/1').flush(alumno);
  http.expectOne('/api/v1/alumnos/1/documentos').flush(documentos);
  http.expectOne('/api/v1/alumnos/1/estado/historial').flush([]);
  http.expectOne('/api/v1/alumnos/1/salud').flush({ mensaje: 'sin ficha' }, { status: 404, statusText: 'Not Found' });
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('AlumnoDetalleComponent: expediente', () => {
  it('muestra "Falta" para los documentos requeridos que no se subieron y marca el expediente incompleto', async () => {
    const el = await montar([doc(1, 'DNI', true)]);
    const texto = el.textContent ?? '';
    expect(texto).toContain('Mendoza Ríos, Lucía');
    expect(texto).toContain('Expediente incompleto');
    expect(texto).toContain('PARTIDA NACIMIENTO: Falta');
    expect(texto).toContain('FICHA SIAGIE: Falta');
    expect(texto).toContain('DNI: Verificado');
  });

  it('un documento subido pero sin verificar todavía no completa el expediente', async () => {
    const el = await montar([doc(1, 'DNI', true), doc(2, 'PARTIDA_NACIMIENTO', false), doc(3, 'FICHA_SIAGIE', true)]);
    const texto = el.textContent ?? '';
    expect(texto).toContain('PARTIDA NACIMIENTO: Subido, sin verificar');
    expect(texto).toContain('Expediente incompleto');
  });

  it('con los tres documentos verificados el expediente queda completo', async () => {
    const el = await montar([doc(1, 'DNI', true), doc(2, 'PARTIDA_NACIMIENTO', true), doc(3, 'FICHA_SIAGIE', true)]);
    expect(el.textContent).toContain('Expediente completo');
  });

  it('sin ficha de salud (404) ofrece registrarla en vez de mostrar un error', async () => {
    const el = await montar([]);
    expect(el.textContent).toContain('Registrar ficha');
    expect(el.textContent).toContain('Todavía no hay ficha de salud');
  });
});
