import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Company } from '../../models/company';
import { CompanyFormComponent, CompanyFormValue } from './company-form.component';

@Component({
  standalone: true,
  imports: [CompanyFormComponent],
  template: `
    <app-company-form
      [mode]="mode"
      [initialValue]="initialValue"
      (submitted)="onSubmitted($event)"
    ></app-company-form>
  `,
})
class HostComponent {
  mode: 'create' | 'edit' = 'create';
  initialValue?: Company;
  submittedValue?: CompanyFormValue;

  onSubmitted(value: CompanyFormValue): void {
    this.submittedValue = value;
  }
}

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

describe('CompanyFormComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('renders the admin email field in create mode', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const labels = Array.from(fixture.nativeElement.querySelectorAll('label')).map((l) =>
      (l as HTMLLabelElement).textContent?.trim(),
    );
    expect(labels).toContain('Email del admin');
  });

  it('does not render the admin email field in edit mode', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.mode = 'edit';
    fixture.detectChanges();
    const labels = Array.from(fixture.nativeElement.querySelectorAll('label')).map((l) =>
      (l as HTMLLabelElement).textContent?.trim(),
    );
    expect(labels).not.toContain('Email del admin');
  });

  it('prefills the form from initialValue', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.mode = 'edit';
    fixture.componentInstance.initialValue = mockCompany;
    fixture.detectChanges();
    const firstInput = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(firstInput.value).toBe('Heladería Lucca');
  });

  it('marks fields touched and does not emit submitted when the form is invalid', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(fixture.componentInstance.submittedValue).toBeUndefined();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('emits submitted with the form value when valid', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const component = fixture.debugElement.children[0].componentInstance as CompanyFormComponent;
    component.form.setValue({
      nombre: 'Heladería Lucca',
      razonSocial: 'Heladería Lucca S.R.L.',
      pais: 'Argentina',
      moneda: 'ARS',
      zonaHoraria: 'America/Buenos_Aires',
      plan: '',
      adminEmail: 'admin@lucca.com',
    });
    fixture.detectChanges();
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(fixture.componentInstance.submittedValue).toEqual({
      nombre: 'Heladería Lucca',
      razonSocial: 'Heladería Lucca S.R.L.',
      pais: 'Argentina',
      moneda: 'ARS',
      zonaHoraria: 'America/Buenos_Aires',
      plan: '',
      adminEmail: 'admin@lucca.com',
    });
  });
});
