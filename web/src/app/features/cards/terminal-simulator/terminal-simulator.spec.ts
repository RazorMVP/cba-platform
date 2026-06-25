import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TerminalSimulatorComponent } from './terminal-simulator';
import { CardsService, SimulateResponse } from '../cards.service';

type Svc = Record<
  'simulatePurchase' | 'simulateWithdrawal' | 'simulateBalance' | 'simulateReversal' | 'networkSignOn' | 'networkEcho',
  ReturnType<typeof vi.fn>
>;

function resp(over: Partial<SimulateResponse> = {}): SimulateResponse {
  return {
    responseCode: '00', responseDescription: 'Approved', authCode: '123456',
    availableBalance: 5000, stan: '000001', rrn: 'rrn1', hexDump: 'DEADBEEF', ...over,
  };
}

describe('TerminalSimulatorComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      simulatePurchase: vi.fn().mockReturnValue(of(resp())),
      simulateWithdrawal: vi.fn().mockReturnValue(of(resp())),
      simulateBalance: vi.fn().mockReturnValue(of(resp())),
      simulateReversal: vi.fn().mockReturnValue(of(resp())),
      networkSignOn: vi.fn().mockReturnValue(of(resp())),
      networkEcho: vi.fn().mockReturnValue(of(resp())),
    };
    TestBed.configureTestingModule({
      imports: [TerminalSimulatorComponent],
      providers: [provideRouter([]), { provide: CardsService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(TerminalSimulatorComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('renders with a default purchase/chip request and no response', () => {
    const c = make();
    expect(c.txnType).toBe('purchase');
    expect(c.entryMode).toBe('CHIP');
    expect(c.response).toBeNull();
  });

  describe('send dispatches to the right simulate method', () => {
    it('purchase merges the current entry mode and stores the response', () => {
      const c = make();
      c.entryMode = 'CONTACTLESS';
      c.send();
      expect(svc.simulatePurchase).toHaveBeenCalledWith(expect.objectContaining({ entryMode: 'CONTACTLESS' }));
      expect(c.response?.responseCode).toBe('00');
      expect(c.loading).toBe(false);
    });

    it('routes each txn type to its own service call', () => {
      const c = make();
      c.txnType = 'withdrawal'; c.send();
      expect(svc.simulateWithdrawal).toHaveBeenCalled();
      c.txnType = 'balance'; c.send();
      expect(svc.simulateBalance).toHaveBeenCalled();
      c.txnType = 'reversal'; c.send();
      expect(svc.simulateReversal).toHaveBeenCalled();
      c.txnType = 'signon'; c.send();
      expect(svc.networkSignOn).toHaveBeenCalled();
      c.txnType = 'echo'; c.send();
      expect(svc.networkEcho).toHaveBeenCalled();
    });

    it('clears loading and leaves response null on error', () => {
      svc.simulatePurchase.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.send();
      expect(c.loading).toBe(false);
      expect(c.response).toBeNull();
    });
  });

  it('approved reflects a 00 response code', () => {
    const c = make();
    expect(c.approved).toBe(false);
    c.response = resp({ responseCode: '00' });
    expect(c.approved).toBe(true);
    c.response = resp({ responseCode: '05' });
    expect(c.approved).toBe(false);
  });

  it('needsCardFields hides card inputs for network messages', () => {
    const c = make();
    c.txnType = 'purchase';
    expect(c.needsCardFields).toBe(true);
    c.txnType = 'signon';
    expect(c.needsCardFields).toBe(false);
    c.txnType = 'echo';
    expect(c.needsCardFields).toBe(false);
  });

  it('needsAmount is only true for purchase and withdrawal', () => {
    const c = make();
    c.txnType = 'purchase';
    expect(c.needsAmount).toBe(true);
    c.txnType = 'withdrawal';
    expect(c.needsAmount).toBe(true);
    c.txnType = 'balance';
    expect(c.needsAmount).toBe(false);
    c.txnType = 'reversal';
    expect(c.needsAmount).toBe(false);
  });
});
