import { Component, EventEmitter, Input, Output } from '@angular/core';

import { ButtonDirective } from '../../../../shared/components/button/button.directive';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';

@Component({
  selector: 'app-company-created-modal',
  standalone: true,
  imports: [ModalComponent, ButtonDirective],
  templateUrl: './company-created-modal.component.html',
})
export class CompanyCreatedModalComponent {
  @Input({ required: true }) adminEmail!: string;
  @Input({ required: true }) temporaryPassword!: string;
  @Output() closed = new EventEmitter<void>();

  copied = false;

  copyPassword(): void {
    navigator.clipboard.writeText(this.temporaryPassword);
    this.copied = true;
  }
}
