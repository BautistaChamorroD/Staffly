import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { Company } from '../../models/company';

export interface CompanyFormValue {
  nombre: string;
  razonSocial: string;
  pais: string;
  moneda: string;
  zonaHoraria: string;
  plan: string;
  adminEmail: string;
}

@Component({
  selector: 'app-company-form',
  standalone: true,
  imports: [ReactiveFormsModule, InputComponent, ButtonDirective],
  templateUrl: './company-form.component.html',
})
export class CompanyFormComponent implements OnInit {
  @Input() mode: 'create' | 'edit' = 'create';
  @Input() initialValue?: Company;
  @Output() submitted = new EventEmitter<CompanyFormValue>();

  private fb = inject(FormBuilder);

  form = this.fb.group({
    nombre: ['', Validators.required],
    razonSocial: ['', Validators.required],
    pais: ['', Validators.required],
    moneda: ['', Validators.required],
    zonaHoraria: ['', Validators.required],
    plan: [''],
    adminEmail: [''],
  });

  get nombreCtrl() {
    return this.form.get('nombre')!;
  }
  get razonSocialCtrl() {
    return this.form.get('razonSocial')!;
  }
  get paisCtrl() {
    return this.form.get('pais')!;
  }
  get monedaCtrl() {
    return this.form.get('moneda')!;
  }
  get zonaHorariaCtrl() {
    return this.form.get('zonaHoraria')!;
  }
  get adminEmailCtrl() {
    return this.form.get('adminEmail')!;
  }

  get adminEmailErrorMessage(): string | undefined {
    if (!this.adminEmailCtrl.invalid || !this.adminEmailCtrl.touched) {
      return undefined;
    }
    if (this.adminEmailCtrl.errors?.['required']) {
      return 'El email del admin es requerido.';
    }
    if (this.adminEmailCtrl.errors?.['email']) {
      return 'Ingresá un email válido.';
    }
    return undefined;
  }

  ngOnInit(): void {
    if (this.mode === 'create') {
      this.adminEmailCtrl.addValidators([Validators.required, Validators.email]);
      this.adminEmailCtrl.updateValueAndValidity();
    }
    if (this.initialValue) {
      this.form.patchValue({
        nombre: this.initialValue.nombre,
        razonSocial: this.initialValue.razonSocial,
        pais: this.initialValue.pais,
        moneda: this.initialValue.moneda,
        zonaHoraria: this.initialValue.zonaHoraria,
        plan: this.initialValue.plan ?? '',
      });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitted.emit(this.form.getRawValue() as CompanyFormValue);
  }
}
