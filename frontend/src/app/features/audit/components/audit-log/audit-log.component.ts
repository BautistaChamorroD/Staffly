import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { AuditLogEntry } from '../../models/audit-log';
import { AuditService } from '../../services/audit.service';

@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [FormsModule, ButtonDirective, InputComponent],
  templateUrl: './audit-log.component.html',
})
export class AuditLogComponent implements OnInit {
  private auditService = inject(AuditService);

  entries: AuditLogEntry[] = [];
  loading = true;
  loadError: string | null = null;

  filterEntidad = '';
  filterEntidadId = '';
  filterUserId = '';
  filterDesde = '';
  filterHasta = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.loadError = null;
    const filters: Record<string, string> = {};
    if (this.filterEntidad)   filters['entidad']   = this.filterEntidad;
    if (this.filterEntidadId) filters['entidadId'] = this.filterEntidadId;
    if (this.filterUserId)    filters['userId']    = this.filterUserId;
    if (this.filterDesde)     filters['desde']     = this.filterDesde;
    if (this.filterHasta)     filters['hasta']     = this.filterHasta;
    this.auditService.list(filters).subscribe({
      next: (entries) => {
        this.entries = entries;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = 'No se pudo cargar el registro de auditoría. Intentá de nuevo.';
      },
    });
  }

  applyFilters(): void {
    this.load();
  }

  clearFilters(): void {
    this.filterEntidad = '';
    this.filterEntidadId = '';
    this.filterUserId = '';
    this.filterDesde = '';
    this.filterHasta = '';
    this.load();
  }
}
