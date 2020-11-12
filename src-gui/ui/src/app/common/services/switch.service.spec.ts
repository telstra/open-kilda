import { TestBed, inject } from '@angular/core/testing';

import { SwitchService } from './switch.service';

describe('SwitchService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SwitchService]
    });
  });

  it('should be created', inject([SwitchService], (service: SwitchService) => {
    expect(service).toBeTruthy();
  }));
});
