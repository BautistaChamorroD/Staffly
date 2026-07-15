import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { Company, CreateCompanyResponse } from '../../models/company';
import { CompanyService } from '../../services/company.service';
import { CompaniesListComponent } from './companies-list.component';

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

describe('CompaniesListComponent', () => {
  let companyServiceStub: {
    list: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    updateStatus: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    companyServiceStub = {
      list: vi.fn().mockReturnValue(of([mockCompany])),
      create: vi.fn(),
      update: vi.fn(),
      updateStatus: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [CompaniesListComponent],
      providers: [{ provide: CompanyService, useValue: companyServiceStub }],
    });
  });

  it('shows the loaded companies in the table', () => {
    const fixture = TestBed.createComponent(CompaniesListComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Heladería Lucca');
  });

  it('shows a loading error banner when the list request fails', () => {
    companyServiceStub.list.mockReturnValue(throwError(() => new Error('network error')));
    const fixture = TestBed.createComponent(CompaniesListComponent);
    fixture.detectChanges();
    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('No se pudo cargar');
  });

  it('opens the create modal, and on successful submit shows the created-password modal and refreshes the list', () => {
    const createResponse: CreateCompanyResponse = {
      company: { ...mockCompany, id: '2', nombre: 'Nueva Empresa' },
      adminEmail: 'admin@nueva.com',
      adminTemporaryPassword: 'temp123',
    };
    companyServiceStub.create.mockReturnValue(of(createResponse));

    const fixture = TestBed.createComponent(CompaniesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openCreateModal();
    fixture.detectChanges();
    expect(fixture.componentInstance.formMode).toBe('create');

    fixture.componentInstance.handleFormSubmit({
      nombre: 'Nueva Empresa',
      razonSocial: 'Nueva Empresa S.A.',
      pais: 'Argentina',
      moneda: 'ARS',
      zonaHoraria: 'America/Buenos_Aires',
      plan: '',
      adminEmail: 'admin@nueva.com',
    });
    fixture.detectChanges();

    expect(companyServiceStub.create).toHaveBeenCalled();
    expect(fixture.componentInstance.formMode).toBeNull();
    expect(fixture.componentInstance.createdModal).toEqual({
      adminEmail: 'admin@nueva.com',
      temporaryPassword: 'temp123',
    });
    expect(companyServiceStub.list).toHaveBeenCalledTimes(2);
  });

  it('shows a form error banner without closing the modal when create fails', () => {
    companyServiceStub.create.mockReturnValue(throwError(() => new Error('validation error')));
    const fixture = TestBed.createComponent(CompaniesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openCreateModal();
    fixture.detectChanges();
    fixture.componentInstance.handleFormSubmit({
      nombre: 'X',
      razonSocial: 'X',
      pais: 'X',
      moneda: 'X',
      zonaHoraria: 'X',
      plan: '',
      adminEmail: 'x@x.com',
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.formMode).toBe('create');
    expect(fixture.componentInstance.formError).toContain('No se pudo crear');
  });

  it('opens the edit modal prefilled with the selected company', () => {
    const fixture = TestBed.createComponent(CompaniesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openEditModal(mockCompany);

    expect(fixture.componentInstance.formMode).toBe('edit');
    expect(fixture.componentInstance.formInitialValue).toEqual(mockCompany);
  });

  it('opens the status confirmation and applies the opposite estado on confirm', () => {
    companyServiceStub.updateStatus.mockReturnValue(of({ ...mockCompany, estado: 'SUSPENDIDA' }));
    const fixture = TestBed.createComponent(CompaniesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openStatusConfirm(mockCompany);
    expect(fixture.componentInstance.statusTarget).toEqual(mockCompany);

    fixture.componentInstance.confirmStatusChange();

    expect(companyServiceStub.updateStatus).toHaveBeenCalledWith('1', { estado: 'SUSPENDIDA' });
    expect(fixture.componentInstance.statusTarget).toBeNull();
  });
});
