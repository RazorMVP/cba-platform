import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SchemeConfigComponent } from './scheme-config';

/**
 * SchemeConfigComponent is a static, data-only accordion — no service injection.
 * Coverage focuses on the seeded scheme catalogue and the expand/collapse toggle.
 */
describe('SchemeConfigComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SchemeConfigComponent],
      providers: [provideRouter([])],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(SchemeConfigComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('renders the five scheme catalogue rows with nothing expanded', () => {
    const c = make();
    expect(c.schemes).toHaveLength(5);
    expect(c.schemes.map(s => s.code)).toEqual(['VISA', 'MASTERCARD', 'VERVE', 'AFRIGO', 'UNIONPAY']);
    expect(c.expanded).toBeNull();
  });

  it('each scheme carries adapter, packager XML and capabilities', () => {
    const c = make();
    const visa = c.schemes.find(s => s.code === 'VISA')!;
    expect(visa.adapter).toBe('VisaSchemeAdapter');
    expect(visa.packagerXml).toBe('iso8583-visa.xml');
    expect(visa.capabilities.length).toBeGreaterThan(0);
  });

  describe('toggle', () => {
    it('expands a scheme then collapses it on second toggle', () => {
      const c = make();
      c.toggle('VISA');
      expect(c.expanded).toBe('VISA');
      c.toggle('VISA');
      expect(c.expanded).toBeNull();
    });

    it('switches the expanded scheme when a different code is toggled', () => {
      const c = make();
      c.toggle('VISA');
      c.toggle('UNIONPAY');
      expect(c.expanded).toBe('UNIONPAY');
    });
  });
});
