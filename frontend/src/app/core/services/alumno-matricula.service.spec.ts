import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AlumnoService } from './alumno.service';
import { MatriculaService } from './matricula.service';

function entorno() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  return { http: TestBed.inject(HttpTestingController), alumnos: TestBed.inject(AlumnoService), matriculas: TestBed.inject(MatriculaService) };
}

describe('AlumnoService', () => {
  it('normaliza la página de alumnos-ms (`total`) a `totalElementos`', () => {
    const { http, alumnos } = entorno();
    let pagina: unknown;
    alumnos.listar('mendoza', 'ACTIVO', 2, 10).subscribe(p => (pagina = p));
    const req = http.expectOne(r => r.url === '/api/v1/alumnos');
    expect(req.request.params.get('q')).toBe('mendoza');
    expect(req.request.params.get('estado')).toBe('ACTIVO');
    expect(req.request.params.get('page')).toBe('2');
    req.flush({ contenido: [], pagina: 2, tamano: 10, total: 41, totalPaginas: 5 });
    expect(pagina).toEqual({ contenido: [], pagina: 2, tamano: 10, totalElementos: 41, totalPaginas: 5 });
  });

  it('no manda filtros vacíos', () => {
    const { http, alumnos } = entorno();
    alumnos.listar().subscribe();
    const req = http.expectOne(r => r.url === '/api/v1/alumnos');
    expect(req.request.params.has('q')).toBe(false);
    expect(req.request.params.has('estado')).toBe(false);
    req.flush({ contenido: [], pagina: 0, tamano: 15, total: 0, totalPaginas: 0 });
  });

  it('sube el documento como multipart con el campo "archivo" y el tipo en la URL', () => {
    const { http, alumnos } = entorno();
    const archivo = new File(['%PDF'], 'dni.pdf', { type: 'application/pdf' });
    alumnos.subirDocumento(7, 'DNI', archivo).subscribe();
    const req = http.expectOne(r => r.url === '/api/v1/alumnos/7/documentos');
    expect(req.request.params.get('tipo')).toBe('DNI');
    expect(req.request.body instanceof FormData).toBe(true);
    expect((req.request.body as FormData).get('archivo')).toBeInstanceOf(File);
    req.flush({});
  });
});

describe('MatriculaService', () => {
  it('arma los filtros de la lista y omite los vacíos', () => {
    const { http, matriculas } = entorno();
    matriculas.listar({ anioId: 1, gradoId: 0, estado: '', texto: 'lucia', page: 1 }).subscribe();
    const req = http.expectOne(r => r.url === '/api/v1/matriculas');
    expect(req.request.params.get('anioId')).toBe('1');
    expect(req.request.params.get('texto')).toBe('lucia');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.has('gradoId')).toBe(false);
    expect(req.request.params.has('estado')).toBe(false);
    req.flush({ contenido: [], pagina: 1, tamano: 15, totalElementos: 0, totalPaginas: 0 });
  });

  it('traslado, retiro y requisito usan los cuerpos que espera matricula-ms', () => {
    const { http, matriculas } = entorno();
    matriculas.trasladar(3, 9, 'Cambio de turno').subscribe();
    expect(http.expectOne('/api/v1/matriculas/3/traslado').request.body).toEqual({ nuevaSeccionId: 9, motivo: 'Cambio de turno' });
    matriculas.retirar(3, 'Mudanza').subscribe();
    expect(http.expectOne('/api/v1/matriculas/3/retiro').request.body).toEqual({ motivo: 'Mudanza' });
    matriculas.marcarRequisito(3, 'FICHA_FIRMADA', true).subscribe();
    const r = http.expectOne('/api/v1/matriculas/3/requisitos/FICHA_FIRMADA');
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toEqual({ cumplido: true, observacion: undefined });
  });
});

describe('CobroService', () => {
  it('el reporte de cobranza pide el día completo (pago-ms recibe fecha y hora)', async () => {
    const { CobroService } = await import('./cobro.service');
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    TestBed.inject(CobroService).reporteCobranza('2026-10-01', '2026-10-31', 'EFECTIVO').subscribe();
    const req = http.expectOne(r => r.url === '/api/v1/pagos/reportes/cobranza');
    expect(req.request.params.get('desde')).toBe('2026-10-01T00:00:00');
    expect(req.request.params.get('hasta')).toBe('2026-10-31T23:59:59');
    expect(req.request.params.get('medio')).toBe('EFECTIVO');
    req.flush({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 });
  });
});
