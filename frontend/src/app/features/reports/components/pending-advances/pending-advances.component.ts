import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ReportService } from '../../services/report.service';
import { PendingAdvanceRow } from '../../models/report';

@Component({
  selector: 'app-pending-advances',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './pending-advances.component.html',
})
export class PendingAdvancesComponent {
  // FE-4.4: implementar tabla y exportación
}
