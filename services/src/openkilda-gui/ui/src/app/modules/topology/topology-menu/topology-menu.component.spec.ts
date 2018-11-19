import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { TopologyMenuComponent } from './topology-menu.component';

describe('TopologyMenuComponent', () => {
  let component: TopologyMenuComponent;
  let fixture: ComponentFixture<TopologyMenuComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ TopologyMenuComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(TopologyMenuComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
