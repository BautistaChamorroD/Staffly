import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ModalComponent } from './modal.component';

@Component({
  standalone: true,
  imports: [ModalComponent],
  template: `
    <ui-modal title="Confirmar" (closed)="onClosed()">
      <p>Cuerpo del modal</p>
    </ui-modal>
  `,
})
class HostComponent {
  closedCount = 0;

  onClosed(): void {
    this.closedCount++;
  }
}

describe('ModalComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('renders the title and projected body', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h3')?.textContent).toBe('Confirmar');
    expect(fixture.nativeElement.textContent).toContain('Cuerpo del modal');
  });

  it('emits closed when the backdrop is clicked', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const backdrop = fixture.nativeElement.querySelector('[data-testid="modal-backdrop"]') as HTMLElement;
    backdrop.click();
    expect(fixture.componentInstance.closedCount).toBe(1);
  });

  it('does not emit closed when the dialog body is clicked', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('[data-testid="modal-dialog"]') as HTMLElement;
    dialog.click();
    expect(fixture.componentInstance.closedCount).toBe(0);
  });

  it('emits closed when the close button is clicked', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const closeButton = fixture.nativeElement.querySelector('[aria-label="Cerrar"]') as HTMLButtonElement;
    closeButton.click();
    expect(fixture.componentInstance.closedCount).toBe(1);
  });

  it('emits closed when Escape is pressed', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(fixture.componentInstance.closedCount).toBe(1);
  });
});
