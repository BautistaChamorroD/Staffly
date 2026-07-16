import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../../environments/environment';
import {
  Branch,
  CreateBranchRequest,
  UpdateBranchRequest,
  UpdateBranchStatusRequest,
} from '../models/branch';
import { BranchService } from './branch.service';

const baseUrl = `${environment.apiUrl}/branches`;

const mockBranch: Branch = {
  id: '1',
  nombre: 'Casa Central',
  direccion: 'Av. Colón 1240',
  zonaHoraria: 'America/Buenos_Aires',
  estado: 'ACTIVA',
  horarioVisibleInicio: '10:00:00',
  horarioVisibleFin: '02:00:00',
};

describe('BranchService', () => {
  let service: BranchService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BranchService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists branches with a GET to /branches', () => {
    service.list().subscribe((branches) => expect(branches).toEqual([mockBranch]));
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('GET');
    req.flush([mockBranch]);
  });

  it('creates a branch with a POST to /branches', () => {
    const request: CreateBranchRequest = {
      nombre: 'Casa Central',
      direccion: 'Av. Colón 1240',
      zonaHoraria: 'America/Buenos_Aires',
    };
    service.create(request).subscribe((branch) => expect(branch).toEqual(mockBranch));
    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockBranch);
  });

  it('updates a branch with a PATCH to /branches/{id}', () => {
    const request: UpdateBranchRequest = { nombre: 'Nuevo nombre' };
    service.update('1', request).subscribe((branch) => expect(branch).toEqual(mockBranch));
    const req = httpMock.expectOne(`${baseUrl}/1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(mockBranch);
  });

  it('updates a branch status with a PATCH to /branches/{id}/status', () => {
    const request: UpdateBranchStatusRequest = { estado: 'INACTIVA' };
    service.updateStatus('1', request).subscribe((branch) => expect(branch).toEqual(mockBranch));
    const req = httpMock.expectOne(`${baseUrl}/1/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(mockBranch);
  });
});
