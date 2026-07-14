import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';

import { InputComponent } from './input.component';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, InputComponent],
  template: `
    <form [formGroup]="form">
      <ui-input
        formControlName="email"
        label="Email"
        [errorMessage]="error"
        [autocomplete]="autocomplete"
      ></ui-input>
    </form>
  `,
})
class HostComponent {
  form = new FormGroup({ email: new FormControl('') });
  error?: string;
  autocomplete?: string;
}

describe('InputComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('reflects the form control\'s initial value into the native input', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.form.get('email')!.setValue('ana@staffly.com');
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.value).toBe('ana@staffly.com');
  });

  it('propagates typed input back to the form control', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.value = 'nuevo@staffly.com';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(fixture.componentInstance.form.get('email')!.value).toBe('nuevo@staffly.com');
  });

  it('associates the label with the input via for/id', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const label = fixture.nativeElement.querySelector('label') as HTMLLabelElement;
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(label.htmlFor).toBe(input.id);
    expect(input.id).toBeTruthy();
  });

  it('shows the error message with role alert when errorMessage is set', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.error = 'El email es requerido.';
    fixture.detectChanges();
    const alert = fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement;
    expect(alert.textContent?.trim()).toBe('El email es requerido.');
  });

  it('renders no error element when errorMessage is not set', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('disables the native input when the form control is disabled', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.get('email')!.disable();
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.disabled).toBe(true);
  });

  it('marks the form control as touched when the native input is blurred', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(fixture.componentInstance.form.get('email')!.touched).toBe(true);
  });

  it('forwards the autocomplete input to the native input attribute', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.autocomplete = 'username';
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.getAttribute('autocomplete')).toBe('username');
  });
});
