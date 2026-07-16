import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Branch } from '../../models/branch';
import { BranchFormComponent, BranchFormValue } from './branch-form.component';

@Component({
  standalone: true,
  imports: [BranchFormComponent],
  template: `
    <app-branch-form
      [mode]="mode"
      [initialValue]="initialValue"
      (submitted)="onSubmitted($event)"
    ></app-branch-form>
  `,
})
class HostComponent {
  mode: 'create' | 'edit' = 'create';
  initialValue?: Branch;
  submittedValue?: BranchFormValue;

  onSubmitted(value: BranchFormValue): void {
    this.submittedValue = value;
  }
}

const mockBranch: Branch = {
  id: '1',
  nombre: 'Casa Central',
  direccion: 'Av. Colón 1240',
  zonaHoraria: 'America/Buenos_Aires',
  estado: 'ACTIVA',
  horarioVisibleInicio: '10:00:00',
  horarioVisibleFin: '02:00:00',
};

describe('BranchFormComponent', () => {
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

  it('prefills the form from initialValue, slicing horario fields down to HH:mm', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.mode = 'edit';
    fixture.componentInstance.initialValue = mockBranch;
    fixture.detectChanges();
    const inputs = fixture.nativeElement.querySelectorAll('input');
    expect((inputs[0] as HTMLInputElement).value).toBe('Casa Central');
    expect((inputs[3] as HTMLInputElement).value).toBe('10:00');
  });

  it('emits submitted with empty strings for the optional horario fields when left blank', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const component = fixture.debugElement.children[0].componentInstance as BranchFormComponent;
    component.form.setValue({
      nombre: 'Casa Central',
      direccion: 'Av. Colón 1240',
      zonaHoraria: 'America/Buenos_Aires',
      horarioVisibleInicio: '',
      horarioVisibleFin: '',
    });
    fixture.detectChanges();
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(fixture.componentInstance.submittedValue).toEqual({
      nombre: 'Casa Central',
      direccion: 'Av. Colón 1240',
      zonaHoraria: 'America/Buenos_Aires',
      horarioVisibleInicio: '',
      horarioVisibleFin: '',
    });
  });
});
