import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { TableComponent } from './table.component';

@Component({
  standalone: true,
  imports: [TableComponent],
  template: `
    <ui-table>
      <thead>
        <tr><th>Nombre</th></tr>
      </thead>
      <tbody>
        <tr><td>Ana Gomez</td></tr>
      </tbody>
    </ui-table>
  `,
})
class HostComponent {}

describe('TableComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('projects the caller\'s thead and tbody inside a native table element', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const table = fixture.nativeElement.querySelector('table') as HTMLTableElement;
    expect(table.querySelector('th')?.textContent).toBe('Nombre');
    expect(table.querySelector('td')?.textContent).toBe('Ana Gomez');
  });

  it('wraps the table in a scrollable, bordered container', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const wrapper = fixture.nativeElement.querySelector('div') as HTMLElement;
    expect(wrapper.className).toContain('overflow-x-auto');
    expect(wrapper.className).toContain('border-brand-line');
  });
});
