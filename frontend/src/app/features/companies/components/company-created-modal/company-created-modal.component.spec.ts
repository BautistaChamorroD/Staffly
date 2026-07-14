import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { CompanyCreatedModalComponent } from './company-created-modal.component';

@Component({
  standalone: true,
  imports: [CompanyCreatedModalComponent],
  template: `
    <app-company-created-modal
      [adminEmail]="email"
      [temporaryPassword]="password"
      (closed)="onClosed()"
    ></app-company-created-modal>
  `,
})
class HostComponent {
  email = 'admin@lucca.com';
  password = 'temp1234';
  closedCount = 0;

  onClosed(): void {
    this.closedCount++;
  }
}

describe('CompanyCreatedModalComponent', () => {
  let writeTextSpy: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    writeTextSpy = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText: writeTextSpy } });
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('renders the admin email and temporary password', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('admin@lucca.com');
    expect(fixture.nativeElement.textContent).toContain('temp1234');
  });

  it('copies the temporary password to the clipboard when "Copiar contraseña" is clicked', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    const copyButton = buttons.find((b) => b.textContent?.includes('Copiar contraseña'))!;
    copyButton.click();
    expect(writeTextSpy).toHaveBeenCalledWith('temp1234');
  });

  it('emits closed when the "Entendido" button is clicked', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    const closeButton = buttons.find((b) => b.textContent?.includes('Entendido'))!;
    closeButton.click();
    expect(fixture.componentInstance.closedCount).toBe(1);
  });
});
