import { Component, OnInit, inject } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { SelectComponent, SelectOption } from '../../../../shared/components/select/select.component';
import { PayrollConfig, TipoConcepto, UpdatePayrollConfigRequest } from '../../models/payroll-config';
import { PayrollConfigService } from '../../services/payroll-config.service';

@Component({
  selector: 'app-payroll-config',
  standalone: true,
  imports: [ReactiveFormsModule, InputComponent, SelectComponent, ButtonDirective, ModalComponent],
  templateUrl: './payroll-config.component.html',
})
export class PayrollConfigComponent implements OnInit {
  private service = inject(PayrollConfigService);
  private fb = inject(FormBuilder);

  loading = true;
  loadError: string | null = null;
  saving = false;
  saveError: string | null = null;
  saveSuccess = false;
  showConfirm = false;

  readonly periodicidadOptions: SelectOption[] = [
    { value: 'MENSUAL', label: 'Mensual' },
    { value: 'QUINCENAL', label: 'Quincenal' },
  ];

  readonly tipoConceptoOptions: SelectOption[] = [
    { value: 'PORCENTAJE', label: 'Porcentaje (%)' },
    { value: 'MONTO_FIJO', label: 'Monto fijo ($)' },
  ];

  form = this.fb.group({
    umbralHorasExtra: ['', [Validators.required, Validators.min(0)]],
    multiplicadorHoraExtra: ['', [Validators.required, Validators.min(1)]],
    multiplicadorFeriado: ['', [Validators.required, Validators.min(1)]],
    periodicidad: ['', Validators.required],
    conceptosDescuento: this.fb.array([]),
  });

  get conceptosArray(): FormArray {
    return this.form.get('conceptosDescuento') as FormArray;
  }

  get conceptoGroups(): FormGroup[] {
    return this.conceptosArray.controls as FormGroup[];
  }

  ngOnInit(): void {
    this.service.get().subscribe({
      next: (config) => {
        this.patchForm(config);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = 'No se pudo cargar la configuración de nómina.';
      },
    });
  }

  addConcepto(): void {
    this.conceptosArray.push(this.buildConceptoGroup());
  }

  removeConcepto(index: number): void {
    this.conceptosArray.removeAt(index);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saveError = null;
    this.saveSuccess = false;

    this.service.hasClosedPeriods().subscribe({
      next: (hasClosed) => {
        if (hasClosed) {
          this.showConfirm = true;
        } else {
          this.doSave();
        }
      },
      error: () => this.doSave(),
    });
  }

  confirmSave(): void {
    this.showConfirm = false;
    this.doSave();
  }

  cancelConfirm(): void {
    this.showConfirm = false;
  }

  private patchForm(config: PayrollConfig): void {
    this.form.patchValue({
      umbralHorasExtra: String(config.umbralHorasExtra),
      multiplicadorHoraExtra: String(config.multiplicadorHoraExtra),
      multiplicadorFeriado: String(config.multiplicadorFeriado),
      periodicidad: config.periodicidad,
    });
    this.conceptosArray.clear();
    for (const c of config.conceptosDescuento) {
      this.conceptosArray.push(this.buildConceptoGroup(c.nombre, c.tipo, c.valor));
    }
  }

  private buildConceptoGroup(
    nombre = '',
    tipo: TipoConcepto = 'PORCENTAJE',
    valor: number | string = 0,
  ): FormGroup {
    return this.fb.group({
      nombre: [nombre, [Validators.required, Validators.maxLength(100)]],
      tipo: [tipo, Validators.required],
      valor: [String(valor), [Validators.required, Validators.min(0)]],
    });
  }

  private doSave(): void {
    this.saving = true;
    this.saveError = null;

    const raw = this.form.getRawValue();
    const request: UpdatePayrollConfigRequest = {
      umbralHorasExtra: Number(raw.umbralHorasExtra),
      multiplicadorHoraExtra: Number(raw.multiplicadorHoraExtra),
      multiplicadorFeriado: Number(raw.multiplicadorFeriado),
      periodicidad: raw.periodicidad as PayrollConfig['periodicidad'],
      conceptosDescuento: (raw.conceptosDescuento as { nombre: string; tipo: string; valor: string }[]).map(
        (c) => ({
          nombre: c.nombre,
          tipo: c.tipo as TipoConcepto,
          valor: Number(c.valor),
        }),
      ),
    };

    this.service.update(request).subscribe({
      next: (config) => {
        this.saving = false;
        this.saveSuccess = true;
        this.patchForm(config);
      },
      error: () => {
        this.saving = false;
        this.saveError = 'No se pudo guardar la configuración. Intentá de nuevo.';
      },
    });
  }
}
