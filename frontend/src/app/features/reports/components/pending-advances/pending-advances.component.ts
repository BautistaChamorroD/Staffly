import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { ExportFormat, PendingAdvanceRow } from '../../models/report';
import { ReportService } from '../../services/report.service';

@Component({
  selector: 'app-pending-advances',
  standalone: true,
  imports: [RouterLink, DecimalPipe, ButtonDirective],
  templateUrl: './pending-advances.component.html',
})
export class PendingAdvancesComponent implements OnInit {
  private reportService = inject(ReportService);

  rows: PendingAdvanceRow[] = [];

  loading = true;
  loadError: string | null = null;

  exportingPdf = false;
  exportingCsv = false;
  exportError: string | null = null;

  get totalMonto(): number {
    return this.rows.reduce((acc, row) => acc + row.monto, 0);
  }

  ngOnInit(): void {
    this.reportService.pendingAdvances().subscribe({
      next: (rows) => {
        this.rows = rows;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = 'No se pudo cargar el reporte. Intentá de nuevo.';
      },
    });
  }

  export(format: ExportFormat): void {
    if (format === 'pdf') this.exportingPdf = true;
    else this.exportingCsv = true;
    this.exportError = null;

    this.reportService.export('pending-advances', format).subscribe({
      next: (blob) => {
        if (format === 'pdf') this.exportingPdf = false;
        else this.exportingCsv = false;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `adelantos-pendientes.${format}`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        if (format === 'pdf') this.exportingPdf = false;
        else this.exportingCsv = false;
        this.exportError = `No se pudo exportar en formato ${format.toUpperCase()}. Intentá de nuevo.`;
      },
    });
  }
}
