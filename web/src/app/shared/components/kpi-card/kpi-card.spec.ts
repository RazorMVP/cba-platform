import { TestBed } from '@angular/core/testing';
import { KpiCardComponent } from './kpi-card';

describe('KpiCardComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [KpiCardComponent] });
  });

  function create() {
    return TestBed.createComponent(KpiCardComponent);
  }

  it('renders title, value and icon', () => {
    const fixture = create();
    fixture.componentRef.setInput('title', 'Total Customers');
    fixture.componentRef.setInput('value', 1234);
    fixture.componentRef.setInput('icon', 'people');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.kpi-card__title')?.textContent?.trim()).toBe('Total Customers');
    expect(el.querySelector('.kpi-card__value')?.textContent?.trim()).toBe('1234');
    expect(el.querySelector('.kpi-card__icon')?.textContent?.trim()).toBe('people');
  });

  it('applies the colour modifier class', () => {
    const fixture = create();
    fixture.componentRef.setInput('color', 'success');
    fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('.kpi-card') as HTMLElement).classList)
      .toContain('kpi-card--success');
  });

  it('hides the footer when there is no subtitle or trend value', () => {
    const fixture = create();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.kpi-card__footer')).toBeNull();
  });

  it('shows the footer + up-trend icon when trending up', () => {
    const fixture = create();
    fixture.componentRef.setInput('trend', 'up');
    fixture.componentRef.setInput('trendValue', '+12%');
    fixture.componentRef.setInput('subtitle', 'vs last month');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const footer = el.querySelector('.kpi-card__footer');
    expect(footer).not.toBeNull();
    expect(footer?.textContent).toContain('+12%');
    expect(footer?.textContent).toContain('vs last month');
    expect(el.querySelector('.kpi-card__trend--up')).not.toBeNull();
    expect(footer?.textContent).toContain('trending_up');
  });
});
