import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuditLogEntry, AuditLogFilters } from '../../models/audit-log';
import { AuditService } from '../../services/audit.service';

@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './audit-log.component.html',
})
export class AuditLogComponent implements OnInit {
  private auditService = inject(AuditService);

  entries: AuditLogEntry[] = [];
  loading = true;
  loadError: string | null = null;

  filters: AuditLogFilters = {};

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.loadError = null;
    this.auditService.list(this.filters).subscribe({
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
}
