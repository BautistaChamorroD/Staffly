import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';

import { SelectComponent, SelectOption } from './select.component';

const testOptions: SelectOption[] = [
  { value: 'A', label: 'Opción A' },
  { value: 'B', label: 'Opción B' },
];

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, SelectComponent],
  template: `
    <form [formGroup]="form">
      <ui-select
        formControlName="choice"
        label="Elegí"
        [options]="options"
        [placeholder]="placeholder"
        [errorMessage]="error"
      ></ui-select>
    </form>
  `,
})
class HostComponent {
  form = new FormGroup({ choice: new FormControl('') });
  options = testOptions;
  placeholder?: string;
  error?: string;
}

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, SelectComponent],
  template: `
    <form [formGroup]="form">
      <ui-select formControlName="choice" label="Elegí" [options]="options"></ui-select>
    </form>
  `,
})
class HostComponentWithInitialValue {
  form = new FormGroup({ choice: new FormControl('B') });
  options = testOptions;
}

describe('SelectComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent, HostComponentWithInitialValue],
    }).compileComponents();
  });

  it('renders the given options', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const optionEls = fixture.nativeElement.querySelectorAll('option');
    const labels = Array.from(optionEls).map((o) => (o as HTMLOptionElement).textContent);
    expect(labels).toEqual(['Opción A', 'Opción B']);
  });

  it("reflects the form control's initial value into the native select", () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.get('choice')!.setValue('B');
    fixture.detectChanges();
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    expect(select.value).toBe('B');
  });

  it('propagates a selected option back to the form control', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    select.value = 'A';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(fixture.componentInstance.form.get('choice')!.value).toBe('A');
  });

  it('renders the placeholder as a disabled first option when set', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.placeholder = 'Seleccionar...';
    fixture.detectChanges();
    const firstOption = fixture.nativeElement.querySelector('option') as HTMLOptionElement;
    expect(firstOption.textContent).toBe('Seleccionar...');
    expect(firstOption.disabled).toBe(true);
  });

  it('shows the error message with role alert when errorMessage is set', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.error = 'Campo requerido.';
    fixture.detectChanges();
    const alert = fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement;
    expect(alert.textContent?.trim()).toBe('Campo requerido.');
  });

  it('selects the correct option on the very first change detection when the initial form value is not the first option', () => {
    const fixture = TestBed.createComponent(HostComponentWithInitialValue);
    fixture.detectChanges();
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    expect(select.value).toBe('B');
  });

  it('does not auto-select the first real option on the very first change detection when the initial value is empty and a placeholder is set', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.placeholder = 'Seleccionar...';
    fixture.detectChanges();
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    expect(select.value).toBe('');
  });
});
