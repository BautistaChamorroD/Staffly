import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { Branch } from '../../models/branch';

export interface BranchFormValue {
  nombre: string;
  direccion: string;
  zonaHoraria: string;
  horarioVisibleInicio: string;
  horarioVisibleFin: string;
}

@Component({
  selector: 'app-branch-form',
  standalone: true,
  imports: [ReactiveFormsModule, InputComponent, ButtonDirective],
  templateUrl: './branch-form.component.html',
})
export class BranchFormComponent implements OnInit {
  @Input() mode: 'create' | 'edit' = 'create';
  @Input() initialValue?: Branch;
  @Output() submitted = new EventEmitter<BranchFormValue>();

  private fb = inject(FormBuilder);

  form = this.fb.group({
    nombre: ['', Validators.required],
    direccion: ['', Validators.required],
    zonaHoraria: ['', Validators.required],
    horarioVisibleInicio: [''],
    horarioVisibleFin: [''],
  });

  get nombreCtrl() {
    return this.form.get('nombre')!;
  }
  get direccionCtrl() {
    return this.form.get('direccion')!;
  }
  get zonaHorariaCtrl() {
    return this.form.get('zonaHoraria')!;
  }

  ngOnInit(): void {
    if (this.initialValue) {
      this.form.patchValue({
        nombre: this.initialValue.nombre,
        direccion: this.initialValue.direccion,
        zonaHoraria: this.initialValue.zonaHoraria,
        horarioVisibleInicio: this.initialValue.horarioVisibleInicio?.slice(0, 5) ?? '',
        horarioVisibleFin: this.initialValue.horarioVisibleFin?.slice(0, 5) ?? '',
      });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitted.emit(this.form.getRawValue() as BranchFormValue);
  }
}
