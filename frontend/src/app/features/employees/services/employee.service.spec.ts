import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../../environments/environment';
import {
  CreateEmployeeRequest,
  Employee,
  UpdateEmployeeRequest,
  UpdateEmployeeStatusRequest,
} from '../models/employee';
import { EmployeeService } from './employee.service';

const baseUrl = `${environment.apiUrl}/employees`;

const mockEmployee: Employee = {
  id: '1',
  nombre: 'Ana',
  apellido: 'Gómez',
  documento: '30111222',
  fechaNacimiento: '1990-01-01',
  fechaIngreso: '2024-01-01',
  fechaEgreso: null,
  tipoContrato: 'JORNADA_COMPLETA',
  categoria: 'Cajera',
  sueldoBase: 500000,
  telefono: null,
  emailContacto: null,
  estadoLaboral: 'ACTIVO',
  estadoLiquidacion: 'AL_DIA',
  branchIds: ['branch-1'],
};

describe('EmployeeService', () => {
  let service: EmployeeService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EmployeeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists employees with a GET to /employees and no query params when no filters are given', () => {
    service.list().subscribe((employees) => expect(employees).toEqual([mockEmployee]));
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys().length).toBe(0);
    req.flush([mockEmployee]);
  });

  it('lists employees with matching query params when filters are given', () => {
    service.list({ estadoLaboral: 'ACTIVO', branchId: 'branch-1', search: 'ana' }).subscribe();
    const req = httpMock.expectOne(
      (r) =>
        r.url === baseUrl &&
        r.params.get('estadoLaboral') === 'ACTIVO' &&
        r.params.get('branchId') === 'branch-1' &&
        r.params.get('search') === 'ana',
    );
    expect(req.request.method).toBe('GET');
    req.flush([mockEmployee]);
  });

  it('creates an employee with a POST to /employees', () => {
    const request: CreateEmployeeRequest = {
      nombre: 'Ana',
      apellido: 'Gómez',
      documento: '30111222',
      fechaNacimiento: '1990-01-01',
      fechaIngreso: '2024-01-01',
      tipoContrato: 'JORNADA_COMPLETA',
      categoria: 'Cajera',
      sueldoBase: 500000,
      branchIds: ['branch-1'],
    };
    service.create(request).subscribe((employee) => expect(employee).toEqual(mockEmployee));
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockEmployee);
  });

  it('updates an employee with a PATCH to /employees/{id}', () => {
    const request: UpdateEmployeeRequest = { categoria: 'Encargada' };
    service.update('1', request).subscribe((employee) => expect(employee).toEqual(mockEmployee));
    const req = httpMock.expectOne(`${baseUrl}/1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(mockEmployee);
  });

  it('updates an employee status with a PATCH to /employees/{id}/status', () => {
    const request: UpdateEmployeeStatusRequest = { estadoLaboral: 'BAJA' };
    service.updateStatus('1', request).subscribe((employee) => expect(employee).toEqual(mockEmployee));
    const req = httpMock.expectOne(`${baseUrl}/1/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(mockEmployee);
  });
});
