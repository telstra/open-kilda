import { TestBed, inject } from '@angular/core/testing';

import { UserActivityService } from './user-activity.service';

describe('UserActivityService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [UserActivityService]
    });
  });

  it('should be created', inject([UserActivityService], (service: UserActivityService) => {
    expect(service).toBeTruthy();
  }));
});
