import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { CardComponent } from './card.component';

@Component({
  standalone: true,
  imports: [CardComponent],
  template: `<ui-card [title]="title">{{ body }}</ui-card>`,
})
class HostComponent {
  title?: string = 'Datos laborales';
  body = 'contenido de la card';
}

describe('CardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('renders the title in an h3 when provided', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const h3 = fixture.nativeElement.querySelector('h3') as HTMLElement;
    expect(h3.textContent?.trim()).toBe('Datos laborales');
  });

  it('does not render an h3 when title is not provided', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.title = undefined;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h3')).toBeNull();
  });

  it('projects the body content', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('contenido de la card');
  });

  it('applies card surface classes to its own host element', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const host = fixture.nativeElement.querySelector('ui-card') as HTMLElement;
    expect(host.className).toContain('rounded-xl');
    expect(host.className).toContain('bg-brand-card');
  });
});
