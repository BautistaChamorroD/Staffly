import { Component, Input } from '@angular/core';

@Component({
  selector: 'ui-card',
  standalone: true,
  templateUrl: './card.component.html',
  host: {
    class: 'block rounded-xl border border-brand-line bg-brand-card p-5',
  },
})
export class CardComponent {
  @Input() title?: string;
}
