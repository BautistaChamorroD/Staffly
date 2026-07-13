import { Component, EventEmitter, HostListener, Input, Output } from '@angular/core';

import { ButtonDirective } from '../button/button.directive';

@Component({
  selector: 'ui-modal',
  standalone: true,
  imports: [ButtonDirective],
  templateUrl: './modal.component.html',
})
export class ModalComponent {
  @Input() title?: string;
  @Output() closed = new EventEmitter<void>();

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }

  onBackdropClick(): void {
    this.closed.emit();
  }

  stopPropagation(event: Event): void {
    event.stopPropagation();
  }
}
