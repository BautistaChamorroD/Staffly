import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';
import { Branch } from '../../../branches/models/branch';
import { BranchService } from '../../../branches/services/branch.service';
import { Employee } from '../../models/employee';
import { EmployeeService } from '../../services/employee.service';
import { EmployeesListComponent } from './employees-list.component';

const mockBranch: Branch = {
  id: 'branch-1',
  nombre: 'Casa Central',
  direccion: 'Av. Colón 1240',
  zonaHoraria: 'America/Buenos_Aires',
  estado: 'ACTIVA',
  horarioVisibleInicio: null,
  horarioVisibleFin: null,
};

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

describe('EmployeesListComponent', () => {
  let employeeServiceStub: {
    list: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    updateStatus: ReturnType<typeof vi.fn>;
  };
  let branchServiceStub: { list: ReturnType<typeof vi.fn> };

  function configure(role: string): void {
    employeeServiceStub = {
      list: vi.fn().mockReturnValue(of([mockEmployee])),
      create: vi.fn(),
      update: vi.fn(),
      updateStatus: vi.fn(),
    };
    branchServiceStub = { list: vi.fn().mockReturnValue(of([mockBranch])) };

    TestBed.configureTestingModule({
      imports: [EmployeesListComponent],
      providers: [
        { provide: EmployeeService, useValue: employeeServiceStub },
        { provide: BranchService, useValue: branchServiceStub },
        { provide: AuthService, useValue: { getRole: () => role } },
      ],
    });
  }

  it('shows the loaded employees in the table, with the branch name resolved', () => {
    configure('ADMIN');
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ana Gómez');
    expect(fixture.nativeElement.textContent).toContain('Casa Central');
  });

  it('shows a loading error banner when the list request fails', () => {
    configure('ADMIN');
    employeeServiceStub.list.mockReturnValue(throwError(() => new Error('network error')));
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();
    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('No se pudo cargar');
  });

  it('debounces the search filter before calling list again', () => {
    vi.useFakeTimers();
    configure('ADMIN');
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();
    expect(employeeServiceStub.list).toHaveBeenCalledTimes(1);

    fixture.componentInstance.filterForm.get('search')!.setValue('ana');
    vi.advanceTimersByTime(299);
    expect(employeeServiceStub.list).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(1);
    expect(employeeServiceStub.list).toHaveBeenCalledTimes(2);
    expect(employeeServiceStub.list).toHaveBeenLastCalledWith({
      estadoLaboral: undefined,
      branchId: undefined,
      search: 'ana',
    });
    vi.useRealTimers();
  });

  it('calls list again with the new value when the estadoLaboral filter changes', () => {
    vi.useFakeTimers();
    configure('ADMIN');
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.filterForm.get('estadoLaboral')!.setValue('BAJA');
    vi.advanceTimersByTime(300);
    expect(employeeServiceStub.list).toHaveBeenLastCalledWith({
      estadoLaboral: 'BAJA',
      branchId: undefined,
      search: undefined,
    });
    vi.useRealTimers();
  });

  it('hides write actions for a SUPERVISOR', () => {
    configure('SUPERVISOR');
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')).map((b) =>
      (b as HTMLButtonElement).textContent?.trim(),
    );
    expect(buttons).not.toContain('+ Nuevo empleado');
    expect(buttons).not.toContain('Editar');
  });

  it('shows write actions for an ADMIN', () => {
    configure('ADMIN');
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')).map((b) =>
      (b as HTMLButtonElement).textContent?.trim(),
    );
    expect(buttons).toContain('+ Nuevo empleado');
    expect(buttons).toContain('Editar');
  });

  it('opens the create modal, and on successful submit refreshes the list', () => {
    configure('ADMIN');
    employeeServiceStub.create.mockReturnValue(of(mockEmployee));
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openCreateModal();
    fixture.detectChanges();
    expect(fixture.componentInstance.formMode).toBe('create');

    fixture.componentInstance.handleFormSubmit({
      nombre: 'Bruno',
      apellido: 'Diaz',
      documento: '30999888',
      fechaNacimiento: '1995-05-05',
      fechaIngreso: '2026-01-01',
      fechaEgreso: '',
      tipoContrato: 'JORNADA_COMPLETA',
      categoria: 'Cajero',
      sueldoBase: 400000,
      telefono: '',
      emailContacto: '',
      branchIds: ['branch-1'],
    });
    fixture.detectChanges();

    expect(employeeServiceStub.create).toHaveBeenCalled();
    expect(fixture.componentInstance.formMode).toBeNull();
    expect(employeeServiceStub.list).toHaveBeenCalledTimes(2);
  });

  it('opens the edit modal prefilled with the selected employee', () => {
    configure('ADMIN');
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openEditModal(mockEmployee);

    expect(fixture.componentInstance.formMode).toBe('edit');
    expect(fixture.componentInstance.formInitialValue).toEqual(mockEmployee);
  });

  it('opens the status modal preloaded with the current estado, and applies the new one on confirm', () => {
    configure('ADMIN');
    employeeServiceStub.updateStatus.mockReturnValue(of({ ...mockEmployee, estadoLaboral: 'BAJA' }));
    const fixture = TestBed.createComponent(EmployeesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openStatusModal(mockEmployee);
    expect(fixture.componentInstance.statusForm.getRawValue().estadoLaboral).toBe('ACTIVO');

    fixture.componentInstance.statusForm.setValue({ estadoLaboral: 'BAJA' });
    fixture.componentInstance.confirmStatusChange();

    expect(employeeServiceStub.updateStatus).toHaveBeenCalledWith('1', { estadoLaboral: 'BAJA' });
    expect(fixture.componentInstance.statusTarget).toBeNull();
  });
});
