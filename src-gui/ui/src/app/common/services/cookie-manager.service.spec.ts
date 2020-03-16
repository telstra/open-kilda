import { TestBed, inject } from '@angular/core/testing';

import { CookieManagerService } from './cookie-manager.service';

describe('CookieManagerService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CookieManagerService]
    });
  });

  it('should be created', inject([CookieManagerService], (service: CookieManagerService) => {
    expect(service).toBeTruthy();
  }));
});
