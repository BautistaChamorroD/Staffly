import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { debounceTime } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';
import { BadgeComponent, BadgeVariant } from '../../../../shared/components/badge/badge.component';
import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { SelectComponent, SelectOption } from '../../../../shared/components/select/select.component';
import { TableComponent } from '../../../../shared/components/table/table.component';
import { Branch } from '../../../branches/models/branch';
import { BranchService } from '../../../branches/services/branch.service';
import { CreateEmployeeRequest, Employee, EstadoLaboral, TipoContrato } from '../../models/employee';
import { EmployeeService } from '../../services/employee.service';
import { EmployeeFormComponent, EmployeeFormValue } from '../employee-form/employee-form.component';

const ESTADO_LABORAL_VARIANTS: Record<EstadoLaboral, BadgeVariant> = {
  ACTIVO: 'success',
  LICENCIA: 'accent',
  SUSPENDIDO: 'warning',
  BAJA: 'neutral',
};

@Component({
  selector: 'app-employees-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    BadgeComponent,
    ModalComponent,
    TableComponent,
    SelectComponent,
    InputComponent,
    EmployeeFormComponent,
  ],
  templateUrl: './employees-list.component.html',
})
export class EmployeesListComponent implements OnInit {
  private employeeService = inject(EmployeeService);
  private branchService = inject(BranchService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);
  private destroyRef = inject(DestroyRef);

  readonly canWrite = this.authService.getRole() === 'ADMIN' || this.authService.getRole() === 'RRHH';

  employees: Employee[] = [];
  branches: Branch[] = [];
  loading = true;
  loadError: string | null = null;
  branchLoadError: string | null = null;

  filterForm = this.fb.group({
    search: [''],
    estadoLaboral: [''],
    branchId: [''],
  });

  readonly estadoLaboralOptions: SelectOption[] = [
    { value: 'ACTIVO', label: 'Activo' },
    { value: 'LICENCIA', label: 'Licencia' },
    { value: 'SUSPENDIDO', label: 'Suspendido' },
    { value: 'BAJA', label: 'Baja' },
  ];

  formMode: 'create' | 'edit' | null = null;
  formInitialValue?: Employee;
  formError: string | null = null;

  statusTarget: Employee | null = null;
  statusForm = this.fb.group({ estadoLaboral: [''] });
  statusError: string | null = null;

  ngOnInit(): void {
    this.branchService.list().subscribe({
      next: (branches) => {
        this.branches = branches;
      },
      error: () => {
        this.branchLoadError = 'No se pudieron cargar las sucursales.';
      },
    });

    this.loadEmployees();

    this.filterForm.valueChanges
      .pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadEmployees());
  }

  get branchFilterOptions(): SelectOption[] {
    return this.branches.map((b) => ({ value: b.id, label: b.nombre }));
  }

  loadEmployees(): void {
    this.loading = true;
    this.loadError = null;
    const filters = this.filterForm.getRawValue();
    this.employeeService
      .list({
        estadoLaboral: (filters.estadoLaboral || undefined) as EstadoLaboral | undefined,
        branchId: filters.branchId || undefined,
        search: filters.search || undefined,
      })
      .subscribe({
        next: (employees) => {
          this.employees = employees;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
          this.loadError = 'No se pudo cargar el listado de empleados.';
        },
      });
  }

  branchNames(employee: Employee): string {
    return employee.branchIds
      .map((id) => this.branches.find((b) => b.id === id)?.nombre)
      .filter((name): name is string => !!name)
      .join(', ');
  }

  estadoLaboralVariant(estado: EstadoLaboral): BadgeVariant {
    return ESTADO_LABORAL_VARIANTS[estado];
  }

  openCreateModal(): void {
    this.formMode = 'create';
    this.formInitialValue = undefined;
    this.formError = null;
  }

  openEditModal(employee: Employee): void {
    this.formMode = 'edit';
    this.formInitialValue = employee;
    this.formError = null;
  }

  closeFormModal(): void {
    this.formMode = null;
  }

  private toRequest(value: EmployeeFormValue): CreateEmployeeRequest {
    return {
      nombre: value.nombre,
      apellido: value.apellido,
      documento: value.documento,
      fechaNacimiento: value.fechaNacimiento,
      fechaIngreso: value.fechaIngreso,
      fechaEgreso: value.fechaEgreso || undefined,
      tipoContrato: value.tipoContrato as TipoContrato,
      categoria: value.categoria,
      sueldoBase: value.sueldoBase,
      telefono: value.telefono || undefined,
      emailContacto: value.emailContacto || undefined,
      branchIds: value.branchIds,
    };
  }

  handleFormSubmit(value: EmployeeFormValue): void {
    this.formError = null;
    const request = this.toRequest(value);

    if (this.formMode === 'create') {
      this.employeeService.create(request).subscribe({
        next: () => {
          this.formMode = null;
          this.loadEmployees();
        },
        error: () => {
          this.formError = 'No se pudo crear el empleado. Intentá de nuevo.';
        },
      });
      return;
    }

    const target = this.formInitialValue;
    if (this.formMode === 'edit' && target) {
      this.employeeService.update(target.id, request).subscribe({
        next: () => {
          this.formMode = null;
          this.loadEmployees();
        },
        error: () => {
          this.formError = 'No se pudo guardar los cambios. Intentá de nuevo.';
        },
      });
    }
  }

  openStatusModal(employee: Employee): void {
    this.statusTarget = employee;
    this.statusForm.setValue({ estadoLaboral: employee.estadoLaboral });
    this.statusError = null;
  }

  closeStatusModal(): void {
    this.statusTarget = null;
  }

  confirmStatusChange(): void {
    const target = this.statusTarget;
    if (!target) {
      return;
    }
    const estadoLaboral = this.statusForm.getRawValue().estadoLaboral as EstadoLaboral;
    this.employeeService.updateStatus(target.id, { estadoLaboral }).subscribe({
      next: () => {
        this.statusTarget = null;
        this.loadEmployees();
      },
      error: () => {
        this.statusError = 'No se pudo cambiar el estado del empleado.';
      },
    });
  }
}
