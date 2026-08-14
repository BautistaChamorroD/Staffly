import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../../../../core/services/auth.service';
import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { SelectComponent, SelectOption } from '../../../../shared/components/select/select.component';
import { TableComponent } from '../../../../shared/components/table/table.component';
import { EmployeeService } from '../../../employees/services/employee.service';
import { DIA_SEMANA_LABELS, DIA_SEMANA_ORDER, DiaSemana, Availability } from '../../models/availability';
import { AvailabilityService } from '../../services/availability.service';

@Component({
  selector: 'app-availability-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    InputComponent,
    ModalComponent,
    SelectComponent,
    TableComponent,
  ],
  templateUrl: './availability-list.component.html',
})
export class AvailabilityListComponent implements OnInit {
  private availabilityService = inject(AvailabilityService);
  private employeeService = inject(EmployeeService);
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);
  private fb = inject(FormBuilder);

  readonly role = this.authService.getRole();
  readonly canWrite = this.role === 'ADMIN' || this.role === 'RRHH' || this.role === 'EMPLOYEE';
  readonly diaLabels = DIA_SEMANA_LABELS;
  readonly diaOrder = DIA_SEMANA_ORDER;

  readonly diaSemanaOptions: SelectOption[] = DIA_SEMANA_ORDER.map((d) => ({
    value: d,
    label: DIA_SEMANA_LABELS[d],
  }));

  employeeId: string | null = null;
  slots: Availability[] = [];
  loading = true;
  loadError: string | null = null;
  resolveError: string | null = null;

  formMode: 'create' | 'edit' | null = null;
  editTarget: Availability | null = null;
  formError: string | null = null;

  deleteTarget: Availability | null = null;
  deleteError: string | null = null;

  slotForm = this.fb.group({
    diaSemana: ['', Validators.required],
    horaInicio: ['', Validators.required],
    horaFin: ['', Validators.required],
  });

  ngOnInit(): void {
    if (this.role === 'EMPLOYEE') {
      this.employeeService.me().subscribe({
        next: (emp) => {
          this.employeeId = emp.id;
          this.loadSlots();
        },
        error: () => {
          this.loading = false;
          this.resolveError = 'No se pudo obtener el empleado vinculado a tu cuenta.';
        },
      });
    } else {
      const employeeId = this.route.snapshot.queryParamMap.get('employeeId');
      if (employeeId) {
        this.employeeId = employeeId;
        this.loadSlots();
      } else {
        this.loading = false;
        this.resolveError = 'Se requiere un empleado para ver la disponibilidad. Accedé desde el listado de empleados.';
      }
    }
  }

  loadSlots(): void {
    if (!this.employeeId) return;
    this.loading = true;
    this.loadError = null;
    this.availabilityService.list(this.employeeId).subscribe({
      next: (slots) => {
        this.slots = slots;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = 'No se pudo cargar la disponibilidad.';
      },
    });
  }

  formatTime(time: string): string {
    return time.substring(0, 5);
  }

  openCreateModal(): void {
    this.formMode = 'create';
    this.editTarget = null;
    this.formError = null;
    this.slotForm.reset();
  }

  openEditModal(slot: Availability): void {
    this.formMode = 'edit';
    this.editTarget = slot;
    this.formError = null;
    this.slotForm.setValue({
      diaSemana: slot.diaSemana,
      horaInicio: this.formatTime(slot.horaInicio),
      horaFin: this.formatTime(slot.horaFin),
    });
  }

  closeFormModal(): void {
    this.formMode = null;
  }

  handleFormSubmit(): void {
    if (this.slotForm.invalid || !this.employeeId) return;
    const raw = this.slotForm.getRawValue();
    this.formError = null;

    const payload = {
      diaSemana: raw.diaSemana as DiaSemana,
      horaInicio: raw.horaInicio!,
      horaFin: raw.horaFin!,
    };

    if (this.formMode === 'create') {
      this.availabilityService.create(this.employeeId, payload).subscribe({
        next: () => {
          this.formMode = null;
          this.loadSlots();
        },
        error: () => {
          this.formError = 'No se pudo guardar la franja. Verificá que no haya solapamiento de horarios.';
        },
      });
    } else if (this.editTarget) {
      this.availabilityService.update(this.employeeId, this.editTarget.id, payload).subscribe({
        next: () => {
          this.formMode = null;
          this.loadSlots();
        },
        error: () => {
          this.formError = 'No se pudo actualizar la franja.';
        },
      });
    }
  }

  openDeleteModal(slot: Availability): void {
    this.deleteTarget = slot;
    this.deleteError = null;
  }

  closeDeleteModal(): void {
    this.deleteTarget = null;
  }

  confirmDelete(): void {
    if (!this.deleteTarget || !this.employeeId) return;
    this.availabilityService.delete(this.employeeId, this.deleteTarget.id).subscribe({
      next: () => {
        this.deleteTarget = null;
        this.loadSlots();
      },
      error: () => {
        this.deleteError = 'No se pudo eliminar la franja. Intentá de nuevo.';
      },
    });
  }
}
