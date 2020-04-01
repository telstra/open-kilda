import { TestBed, inject } from '@angular/core/testing';

import { IslDataService } from './isl-data.service';

describe('IslDataService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [IslDataService]
    });
  });

  it('should be created', inject([IslDataService], (service: IslDataService) => {
    expect(service).toBeTruthy();
  }));
});
