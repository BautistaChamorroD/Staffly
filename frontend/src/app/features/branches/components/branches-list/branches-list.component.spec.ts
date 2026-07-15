import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';
import { Branch } from '../../models/branch';
import { BranchService } from '../../services/branch.service';
import { BranchesListComponent } from './branches-list.component';

const mockBranch: Branch = {
  id: '1',
  nombre: 'Casa Central',
  direccion: 'Av. Colón 1240',
  zonaHoraria: 'America/Buenos_Aires',
  estado: 'ACTIVA',
  horarioVisibleInicio: '10:00:00',
  horarioVisibleFin: '02:00:00',
};

describe('BranchesListComponent', () => {
  let branchServiceStub: {
    list: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    updateStatus: ReturnType<typeof vi.fn>;
  };

  function configure(role: string): void {
    branchServiceStub = {
      list: vi.fn().mockReturnValue(of([mockBranch])),
      create: vi.fn(),
      update: vi.fn(),
      updateStatus: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [BranchesListComponent],
      providers: [
        { provide: BranchService, useValue: branchServiceStub },
        { provide: AuthService, useValue: { getRole: () => role } },
      ],
    });
  }

  it('shows the loaded branches in the table', () => {
    configure('ADMIN');
    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Casa Central');
  });

  it('shows a loading error banner when the list request fails', () => {
    configure('ADMIN');
    branchServiceStub.list.mockReturnValue(throwError(() => new Error('network error')));
    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();
    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('No se pudo cargar');
  });

  it('shows create/edit/status action buttons for an ADMIN', () => {
    configure('ADMIN');
    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')).map((b) =>
      (b as HTMLButtonElement).textContent?.trim(),
    );
    expect(buttons).toContain('+ Nueva sucursal');
    expect(buttons).toContain('Editar');
  });

  it('hides create/edit/status action buttons for a non-ADMIN role', () => {
    configure('RRHH');
    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')).map((b) =>
      (b as HTMLButtonElement).textContent?.trim(),
    );
    expect(buttons).not.toContain('+ Nueva sucursal');
    expect(buttons).not.toContain('Editar');
  });

  it('opens the create modal, and on successful submit refreshes the list', () => {
    configure('ADMIN');
    branchServiceStub.create.mockReturnValue(of(mockBranch));

    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openCreateModal();
    fixture.detectChanges();
    expect(fixture.componentInstance.formMode).toBe('create');

    fixture.componentInstance.handleFormSubmit({
      nombre: 'Sucursal Nueva',
      direccion: 'Calle Falsa 123',
      zonaHoraria: 'America/Buenos_Aires',
      horarioVisibleInicio: '',
      horarioVisibleFin: '',
    });
    fixture.detectChanges();

    expect(branchServiceStub.create).toHaveBeenCalled();
    expect(fixture.componentInstance.formMode).toBeNull();
    expect(branchServiceStub.list).toHaveBeenCalledTimes(2);
  });

  it('shows a form error banner without closing the modal when create fails', () => {
    configure('ADMIN');
    branchServiceStub.create.mockReturnValue(throwError(() => new Error('validation error')));
    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openCreateModal();
    fixture.detectChanges();
    fixture.componentInstance.handleFormSubmit({
      nombre: 'X',
      direccion: 'X',
      zonaHoraria: 'X',
      horarioVisibleInicio: '',
      horarioVisibleFin: '',
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.formMode).toBe('create');
    expect(fixture.componentInstance.formError).toContain('No se pudo crear');
  });

  it('opens the edit modal prefilled with the selected branch', () => {
    configure('ADMIN');
    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openEditModal(mockBranch);

    expect(fixture.componentInstance.formMode).toBe('edit');
    expect(fixture.componentInstance.formInitialValue).toEqual(mockBranch);
  });

  it('opens the status confirmation and applies the opposite estado on confirm', () => {
    configure('ADMIN');
    branchServiceStub.updateStatus.mockReturnValue(of({ ...mockBranch, estado: 'INACTIVA' }));
    const fixture = TestBed.createComponent(BranchesListComponent);
    fixture.detectChanges();

    fixture.componentInstance.openStatusConfirm(mockBranch);
    expect(fixture.componentInstance.statusTarget).toEqual(mockBranch);

    fixture.componentInstance.confirmStatusChange();

    expect(branchServiceStub.updateStatus).toHaveBeenCalledWith('1', { estado: 'INACTIVA' });
    expect(fixture.componentInstance.statusTarget).toBeNull();
  });
});
