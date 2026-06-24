import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PageHeaderComponent } from './page-header';

describe('PageHeaderComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PageHeaderComponent] });
  });

  it('renders the title', () => {
    const fixture = TestBed.createComponent(PageHeaderComponent);
    fixture.componentRef.setInput('title', 'Customers');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.page-header__title')?.textContent?.trim())
      .toBe('Customers');
  });

  it('renders subtitle + icon only when provided', () => {
    const fixture = TestBed.createComponent(PageHeaderComponent);
    fixture.componentRef.setInput('title', 'Customers');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.page-header__subtitle')).toBeNull();
    expect(fixture.nativeElement.querySelector('.page-header__icon')).toBeNull();

    fixture.componentRef.setInput('subtitle', 'Manage clients');
    fixture.componentRef.setInput('icon', 'people');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.page-header__subtitle')?.textContent?.trim())
      .toBe('Manage clients');
    expect(fixture.nativeElement.querySelector('.page-header__icon')?.textContent?.trim())
      .toBe('people');
  });

  it('projects [actions] content', () => {
    @Component({
      standalone: true,
      imports: [PageHeaderComponent],
      template: `<app-page-header title="X"><button actions>New</button></app-page-header>`,
    })
    class HostComponent {}

    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('button[actions]') as HTMLElement;
    expect(btn).not.toBeNull();
    expect(btn.textContent?.trim()).toBe('New');
  });
});
