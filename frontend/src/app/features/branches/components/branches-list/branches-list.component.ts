import { Component, OnInit, inject } from '@angular/core';

import { AuthService } from '../../../../core/services/auth.service';
import { BadgeComponent } from '../../../../shared/components/badge/badge.component';
import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { TableComponent } from '../../../../shared/components/table/table.component';
import {
  Branch,
  CreateBranchRequest,
  EstadoSucursal,
  UpdateBranchRequest,
} from '../../models/branch';
import { BranchService } from '../../services/branch.service';
import { BranchFormComponent, BranchFormValue } from '../branch-form/branch-form.component';

@Component({
  selector: 'app-branches-list',
  standalone: true,
  imports: [ButtonDirective, BadgeComponent, ModalComponent, TableComponent, BranchFormComponent],
  templateUrl: './branches-list.component.html',
})
export class BranchesListComponent implements OnInit {
  private branchService = inject(BranchService);
  private authService = inject(AuthService);

  readonly isAdmin = this.authService.getRole() === 'ADMIN';

  branches: Branch[] = [];
  loading = true;
  loadError: string | null = null;

  formMode: 'create' | 'edit' | null = null;
  formInitialValue?: Branch;
  formError: string | null = null;

  statusTarget: Branch | null = null;
  statusError: string | null = null;

  ngOnInit(): void {
    this.loadBranches();
  }

  loadBranches(): void {
    this.loading = true;
    this.loadError = null;
    this.branchService.list().subscribe({
      next: (branches) => {
        this.branches = branches;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = 'No se pudo cargar el listado de sucursales.';
      },
    });
  }

  openCreateModal(): void {
    this.formMode = 'create';
    this.formInitialValue = undefined;
    this.formError = null;
  }

  openEditModal(branch: Branch): void {
    this.formMode = 'edit';
    this.formInitialValue = branch;
    this.formError = null;
  }

  closeFormModal(): void {
    this.formMode = null;
  }

  handleFormSubmit(value: BranchFormValue): void {
    this.formError = null;

    if (this.formMode === 'create') {
      const request: CreateBranchRequest = {
        nombre: value.nombre,
        direccion: value.direccion,
        zonaHoraria: value.zonaHoraria,
        horarioVisibleInicio: value.horarioVisibleInicio || undefined,
        horarioVisibleFin: value.horarioVisibleFin || undefined,
      };
      this.branchService.create(request).subscribe({
        next: () => {
          this.formMode = null;
          this.loadBranches();
        },
        error: () => {
          this.formError = 'No se pudo crear la sucursal. Intentá de nuevo.';
        },
      });
      return;
    }

    const target = this.formInitialValue;
    if (this.formMode === 'edit' && target) {
      const request: UpdateBranchRequest = {
        nombre: value.nombre,
        direccion: value.direccion,
        zonaHoraria: value.zonaHoraria,
        horarioVisibleInicio: value.horarioVisibleInicio || undefined,
        horarioVisibleFin: value.horarioVisibleFin || undefined,
      };
      this.branchService.update(target.id, request).subscribe({
        next: () => {
          this.formMode = null;
          this.loadBranches();
        },
        error: () => {
          this.formError = 'No se pudo guardar los cambios. Intentá de nuevo.';
        },
      });
    }
  }

  openStatusConfirm(branch: Branch): void {
    this.statusTarget = branch;
    this.statusError = null;
  }

  closeStatusConfirm(): void {
    this.statusTarget = null;
  }

  confirmStatusChange(): void {
    const target = this.statusTarget;
    if (!target) {
      return;
    }
    const nuevoEstado: EstadoSucursal = target.estado === 'ACTIVA' ? 'INACTIVA' : 'ACTIVA';
    this.branchService.updateStatus(target.id, { estado: nuevoEstado }).subscribe({
      next: () => {
        this.statusTarget = null;
        this.loadBranches();
      },
      error: () => {
        this.statusError = 'No se pudo cambiar el estado de la sucursal.';
      },
    });
  }
}
