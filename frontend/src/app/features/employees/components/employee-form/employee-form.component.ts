import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { SelectComponent, SelectOption } from '../../../../shared/components/select/select.component';
import { Branch } from '../../../branches/models/branch';
import { Employee } from '../../models/employee';

export interface EmployeeFormValue {
  nombre: string;
  apellido: string;
  documento: string;
  fechaNacimiento: string;
  fechaIngreso: string;
  fechaEgreso: string;
  tipoContrato: string;
  categoria: string;
  sueldoBase: number;
  telefono: string;
  emailContacto: string;
  branchIds: string[];
}

@Component({
  selector: 'app-employee-form',
  standalone: true,
  imports: [ReactiveFormsModule, InputComponent, SelectComponent, ButtonDirective],
  templateUrl: './employee-form.component.html',
})
export class EmployeeFormComponent implements OnInit {
  @Input() mode: 'create' | 'edit' = 'create';
  @Input() initialValue?: Employee;
  @Input() branches: Branch[] = [];
  @Output() submitted = new EventEmitter<EmployeeFormValue>();

  private fb = inject(FormBuilder);

  readonly tipoContratoOptions: SelectOption[] = [
    { value: 'JORNADA_COMPLETA', label: 'Jornada completa' },
    { value: 'JORNADA_PARCIAL', label: 'Jornada parcial' },
    { value: 'POR_HORA', label: 'Por hora' },
  ];

  form = this.fb.group({
    nombre: ['', Validators.required],
    apellido: ['', Validators.required],
    documento: ['', Validators.required],
    fechaNacimiento: ['', Validators.required],
    fechaIngreso: ['', Validators.required],
    fechaEgreso: [''],
    tipoContrato: ['', Validators.required],
    categoria: ['', Validators.required],
    sueldoBase: ['', Validators.required],
    telefono: [''],
    emailContacto: ['', Validators.email],
  });

  selectedBranchIds: string[] = [];
  branchesError = false;

  get nombreCtrl() {
    return this.form.get('nombre')!;
  }
  get apellidoCtrl() {
    return this.form.get('apellido')!;
  }
  get documentoCtrl() {
    return this.form.get('documento')!;
  }
  get fechaNacimientoCtrl() {
    return this.form.get('fechaNacimiento')!;
  }
  get fechaIngresoCtrl() {
    return this.form.get('fechaIngreso')!;
  }
  get tipoContratoCtrl() {
    return this.form.get('tipoContrato')!;
  }
  get categoriaCtrl() {
    return this.form.get('categoria')!;
  }
  get sueldoBaseCtrl() {
    return this.form.get('sueldoBase')!;
  }
  get emailContactoCtrl() {
    return this.form.get('emailContacto')!;
  }

  ngOnInit(): void {
    if (this.initialValue) {
      this.form.patchValue({
        nombre: this.initialValue.nombre,
        apellido: this.initialValue.apellido,
        documento: this.initialValue.documento,
        fechaNacimiento: this.initialValue.fechaNacimiento,
        fechaIngreso: this.initialValue.fechaIngreso,
        fechaEgreso: this.initialValue.fechaEgreso ?? '',
        tipoContrato: this.initialValue.tipoContrato,
        categoria: this.initialValue.categoria,
        sueldoBase: String(this.initialValue.sueldoBase),
        telefono: this.initialValue.telefono ?? '',
        emailContacto: this.initialValue.emailContacto ?? '',
      });
      this.selectedBranchIds = [...this.initialValue.branchIds];
    }
  }

  isBranchSelected(branchId: string): boolean {
    return this.selectedBranchIds.includes(branchId);
  }

  toggleBranch(branchId: string, checked: boolean): void {
    this.selectedBranchIds = checked
      ? [...this.selectedBranchIds, branchId]
      : this.selectedBranchIds.filter((id) => id !== branchId);
  }

  onSubmit(): void {
    this.branchesError = this.selectedBranchIds.length === 0;
    if (this.form.invalid || this.branchesError) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.submitted.emit({
      nombre: raw.nombre!,
      apellido: raw.apellido!,
      documento: raw.documento!,
      fechaNacimiento: raw.fechaNacimiento!,
      fechaIngreso: raw.fechaIngreso!,
      fechaEgreso: raw.fechaEgreso ?? '',
      tipoContrato: raw.tipoContrato!,
      categoria: raw.categoria!,
      sueldoBase: Number(raw.sueldoBase),
      telefono: raw.telefono ?? '',
      emailContacto: raw.emailContacto ?? '',
      branchIds: this.selectedBranchIds,
    });
  }
}
