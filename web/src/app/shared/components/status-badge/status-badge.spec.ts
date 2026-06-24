import { TestBed } from '@angular/core/testing';
import { StatusBadgeComponent } from './status-badge';

describe('StatusBadgeComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [StatusBadgeComponent] });
  });

  function render(label: string, variant?: string) {
    const fixture = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentRef.setInput('label', label);
    if (variant) fixture.componentRef.setInput('variant', variant);
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('span.badge') as HTMLElement;
  }

  it('renders the label text', () => {
    expect(render('Active').textContent?.trim()).toBe('Active');
  });

  it('applies the variant modifier class', () => {
    expect(render('Approved', 'success').classList).toContain('badge--success');
  });

  it('defaults to the neutral variant', () => {
    expect(render('Pending').classList).toContain('badge--neutral');
  });
});
