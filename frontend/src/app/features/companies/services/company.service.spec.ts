import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../../environments/environment';
import {
  Company,
  CreateCompanyRequest,
  CreateCompanyResponse,
  UpdateCompanyRequest,
  UpdateCompanyStatusRequest,
} from '../models/company';
import { CompanyService } from './company.service';

const baseUrl = `${environment.apiUrl}/companies`;

const mockCompany: Company = {
  id: '1',
  nombre: 'Heladería Lucca',
  razonSocial: 'Heladería Lucca S.R.L.',
  pais: 'Argentina',
  moneda: 'ARS',
  zonaHoraria: 'America/Buenos_Aires',
  estado: 'ACTIVA',
  plan: 'SaaS Inicial',
  fechaAlta: '2026-07-14T00:00:00Z',
};

describe('CompanyService', () => {
  let service: CompanyService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CompanyService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists companies with a GET to /companies', () => {
    service.list().subscribe((companies) => expect(companies).toEqual([mockCompany]));
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('GET');
    req.flush([mockCompany]);
  });

  it('creates a company with a POST to /companies', () => {
    const request: CreateCompanyRequest = {
      nombre: 'Heladería Lucca',
      razonSocial: 'Heladería Lucca S.R.L.',
      pais: 'Argentina',
      moneda: 'ARS',
      zonaHoraria: 'America/Buenos_Aires',
      adminEmail: 'admin@lucca.com',
    };
    const mockResponse: CreateCompanyResponse = {
      company: mockCompany,
      adminEmail: 'admin@lucca.com',
      adminTemporaryPassword: 'temp1234',
    };
    service.create(request).subscribe((response) => expect(response).toEqual(mockResponse));
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockResponse);
  });

  it('updates a company with a PATCH to /companies/{id}', () => {
    const request: UpdateCompanyRequest = { nombre: 'Nuevo nombre' };
    service.update('1', request).subscribe((company) => expect(company).toEqual(mockCompany));
    const req = httpMock.expectOne(`${baseUrl}/1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(mockCompany);
  });

  it('updates a company status with a PATCH to /companies/{id}/status', () => {
    const request: UpdateCompanyStatusRequest = { estado: 'SUSPENDIDA' };
    service.updateStatus('1', request).subscribe((company) => expect(company).toEqual(mockCompany));
    const req = httpMock.expectOne(`${baseUrl}/1/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(mockCompany);
  });
});
