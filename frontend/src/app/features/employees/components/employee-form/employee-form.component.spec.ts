import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Branch } from '../../../branches/models/branch';
import { Employee } from '../../models/employee';
import { EmployeeFormComponent, EmployeeFormValue } from './employee-form.component';

const mockBranches: Branch[] = [
  {
    id: 'branch-1',
    nombre: 'Casa Central',
    direccion: 'Av. Colón 1240',
    zonaHoraria: 'America/Buenos_Aires',
    estado: 'ACTIVA',
    horarioVisibleInicio: null,
    horarioVisibleFin: null,
  },
  {
    id: 'branch-2',
    nombre: 'Sucursal Norte',
    direccion: 'Av. Norte 500',
    zonaHoraria: 'America/Buenos_Aires',
    estado: 'ACTIVA',
    horarioVisibleInicio: null,
    horarioVisibleFin: null,
  },
];

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

@Component({
  standalone: true,
  imports: [EmployeeFormComponent],
  template: `
    <app-employee-form
      [mode]="mode"
      [initialValue]="initialValue"
      [branches]="branches"
      (submitted)="onSubmitted($event)"
    ></app-employee-form>
  `,
})
class HostComponent {
  mode: 'create' | 'edit' = 'create';
  initialValue?: Employee;
  branches: Branch[] = mockBranches;
  submittedValue?: EmployeeFormValue;

  onSubmitted(value: EmployeeFormValue): void {
    this.submittedValue = value;
  }
}

describe('EmployeeFormComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('marks fields touched and does not emit submitted when required fields are missing', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(fixture.componentInstance.submittedValue).toBeUndefined();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('renders one checkbox per branch and requires at least one selected', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const checkboxes = fixture.nativeElement.querySelectorAll('input[type="checkbox"]');
    expect(checkboxes.length).toBe(2);

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Seleccioná al menos una sucursal.');
  });

  it('prefills the form and the checked branches from initialValue', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.mode = 'edit';
    fixture.componentInstance.initialValue = mockEmployee;
    fixture.detectChanges();
    const firstInput = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(firstInput.value).toBe('Ana');
    const checkboxes = fixture.nativeElement.querySelectorAll(
      'input[type="checkbox"]',
    ) as NodeListOf<HTMLInputElement>;
    expect(checkboxes[0].checked).toBe(true);
    expect(checkboxes[1].checked).toBe(false);
  });

  it('emits submitted with the full form value, a numeric sueldoBase, and the checked branch ids', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    const component = fixture.debugElement.children[0].componentInstance as EmployeeFormComponent;
    component.form.setValue({
      nombre: 'Ana',
      apellido: 'Gómez',
      documento: '30111222',
      fechaNacimiento: '1990-01-01',
      fechaIngreso: '2024-01-01',
      fechaEgreso: '',
      tipoContrato: 'JORNADA_COMPLETA',
      categoria: 'Cajera',
      sueldoBase: '500000',
      telefono: '',
      emailContacto: '',
    });
    fixture.detectChanges();

    const checkboxes = fixture.nativeElement.querySelectorAll(
      'input[type="checkbox"]',
    ) as NodeListOf<HTMLInputElement>;
    checkboxes[0].checked = true;
    checkboxes[0].dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.componentInstance.submittedValue).toEqual({
      nombre: 'Ana',
      apellido: 'Gómez',
      documento: '30111222',
      fechaNacimiento: '1990-01-01',
      fechaIngreso: '2024-01-01',
      fechaEgreso: '',
      tipoContrato: 'JORNADA_COMPLETA',
      categoria: 'Cajera',
      sueldoBase: 500000,
      telefono: '',
      emailContacto: '',
      branchIds: ['branch-1'],
    });
  });
});
