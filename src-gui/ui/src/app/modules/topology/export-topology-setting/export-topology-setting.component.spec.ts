import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ExportTopologySettingComponent } from './export-topology-setting.component';

describe('ExportTopologySettingComponent', () => {
  let component: ExportTopologySettingComponent;
  let fixture: ComponentFixture<ExportTopologySettingComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ExportTopologySettingComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ExportTopologySettingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
