import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { UseractivityListComponent } from './useractivity-list.component';

describe('UseractivityListComponent', () => {
  let component: UseractivityListComponent;
  let fixture: ComponentFixture<UseractivityListComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ UseractivityListComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(UseractivityListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
