import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';

import { BadgeComponent } from '../../../../shared/components/badge/badge.component';
import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { TableComponent } from '../../../../shared/components/table/table.component';
import {
  Company,
  CreateCompanyRequest,
  EstadoEmpresa,
  UpdateCompanyRequest,
} from '../../models/company';
import { CompanyService } from '../../services/company.service';
import { CompanyCreatedModalComponent } from '../company-created-modal/company-created-modal.component';
import { CompanyFormComponent, CompanyFormValue } from '../company-form/company-form.component';

@Component({
  selector: 'app-companies-list',
  standalone: true,
  imports: [
    DatePipe,
    ButtonDirective,
    BadgeComponent,
    ModalComponent,
    TableComponent,
    CompanyFormComponent,
    CompanyCreatedModalComponent,
  ],
  templateUrl: './companies-list.component.html',
})
export class CompaniesListComponent implements OnInit {
  private companyService = inject(CompanyService);

  companies: Company[] = [];
  loading = true;
  loadError: string | null = null;

  formMode: 'create' | 'edit' | null = null;
  formInitialValue?: Company;
  formError: string | null = null;

  createdModal: { adminEmail: string; temporaryPassword: string } | null = null;

  statusTarget: Company | null = null;
  statusError: string | null = null;

  ngOnInit(): void {
    this.loadCompanies();
  }

  loadCompanies(): void {
    this.loading = true;
    this.loadError = null;
    this.companyService.list().subscribe({
      next: (companies) => {
        this.companies = companies;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = 'No se pudo cargar el listado de empresas.';
      },
    });
  }

  openCreateModal(): void {
    this.formMode = 'create';
    this.formInitialValue = undefined;
    this.formError = null;
  }

  openEditModal(company: Company): void {
    this.formMode = 'edit';
    this.formInitialValue = company;
    this.formError = null;
  }

  closeFormModal(): void {
    this.formMode = null;
  }

  handleFormSubmit(value: CompanyFormValue): void {
    this.formError = null;

    if (this.formMode === 'create') {
      const request: CreateCompanyRequest = {
        nombre: value.nombre,
        razonSocial: value.razonSocial,
        pais: value.pais,
        moneda: value.moneda,
        zonaHoraria: value.zonaHoraria,
        plan: value.plan || undefined,
        adminEmail: value.adminEmail,
      };
      this.companyService.create(request).subscribe({
        next: (response) => {
          this.formMode = null;
          this.createdModal = {
            adminEmail: response.adminEmail,
            temporaryPassword: response.adminTemporaryPassword,
          };
          this.loadCompanies();
        },
        error: () => {
          this.formError = 'No se pudo crear la empresa. Intentá de nuevo.';
        },
      });
      return;
    }

    const target = this.formInitialValue;
    if (this.formMode === 'edit' && target) {
      const request: UpdateCompanyRequest = {
        nombre: value.nombre,
        razonSocial: value.razonSocial,
        pais: value.pais,
        moneda: value.moneda,
        zonaHoraria: value.zonaHoraria,
        plan: value.plan || undefined,
      };
      this.companyService.update(target.id, request).subscribe({
        next: () => {
          this.formMode = null;
          this.loadCompanies();
        },
        error: () => {
          this.formError = 'No se pudo guardar los cambios. Intentá de nuevo.';
        },
      });
    }
  }

  closeCreatedModal(): void {
    this.createdModal = null;
  }

  openStatusConfirm(company: Company): void {
    this.statusTarget = company;
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
    const nuevoEstado: EstadoEmpresa = target.estado === 'ACTIVA' ? 'SUSPENDIDA' : 'ACTIVA';
    this.companyService.updateStatus(target.id, { estado: nuevoEstado }).subscribe({
      next: () => {
        this.statusTarget = null;
        this.loadCompanies();
      },
      error: () => {
        this.statusError = 'No se pudo cambiar el estado de la empresa.';
      },
    });
  }
}
