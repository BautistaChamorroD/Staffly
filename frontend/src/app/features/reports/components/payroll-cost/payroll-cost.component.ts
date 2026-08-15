import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { InputComponent } from '../../../../shared/components/input/input.component';
import { SelectComponent, SelectOption } from '../../../../shared/components/select/select.component';
import { Branch } from '../../../branches/models/branch';
import { BranchService } from '../../../branches/services/branch.service';
import { ExportFormat, PayrollCostRow } from '../../models/report';
import { ReportService } from '../../services/report.service';

@Component({
  selector: 'app-payroll-cost',
  standalone: true,
  imports: [RouterLink, FormsModule, DecimalPipe, ButtonDirective, InputComponent, SelectComponent],
  templateUrl: './payroll-cost.component.html',
})
export class PayrollCostComponent implements OnInit {
  private reportService = inject(ReportService);
  private branchService = inject(BranchService);

  branches: Branch[] = [];
  rows: PayrollCostRow[] = [];

  filterBranchId = '';
  filterDesde = '';
  filterHasta = '';

  hasLoaded = false;
  loading = false;
  loadError: string | null = null;

  exportingPdf = false;
  exportingCsv = false;
  exportError: string | null = null;

  get branchOptions(): SelectOption[] {
    return [
      { value: '', label: 'Todas las sucursales' },
      ...this.branches.map((b) => ({ value: b.id, label: b.nombre })),
    ];
  }

  get totals() {
    return this.rows.reduce(
      (acc, row) => ({
        cantidadEmpleados: acc.cantidadEmpleados + row.cantidadEmpleados,
        totalBruto: acc.totalBruto + row.totalBruto,
        totalDeducciones: acc.totalDeducciones + row.totalDeducciones,
        totalNeto: acc.totalNeto + row.totalNeto,
      }),
      { cantidadEmpleados: 0, totalBruto: 0, totalDeducciones: 0, totalNeto: 0 },
    );
  }

  ngOnInit(): void {
    this.branchService.list().subscribe({
      next: (branches) => (this.branches = branches),
    });
  }

  generate(): void {
    this.loading = true;
    this.loadError = null;
    const filters: { branchId?: string; desde?: string; hasta?: string } = {};
    if (this.filterBranchId) filters.branchId = this.filterBranchId;
    if (this.filterDesde) filters.desde = this.filterDesde;
    if (this.filterHasta) filters.hasta = this.filterHasta;
    this.reportService.payrollCost(filters).subscribe({
      next: (rows) => {
        this.rows = rows;
        this.hasLoaded = true;
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

    const filters: Record<string, string> = {};
    if (this.filterBranchId) filters['branchId'] = this.filterBranchId;
    if (this.filterDesde) filters['desde'] = this.filterDesde;
    if (this.filterHasta) filters['hasta'] = this.filterHasta;

    this.reportService.export('payroll-cost', format, filters).subscribe({
      next: (blob) => {
        if (format === 'pdf') this.exportingPdf = false;
        else this.exportingCsv = false;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `costo-nomina.${format}`;
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
