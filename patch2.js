const fs = require('fs');

const file = 'frontend-angular/src/app/pages/suite-detail/suite-detail.component.ts';
let code = fs.readFileSync(file, 'utf8');

if (!code.includes('ChangeDetectorRef')) {
  code = code.replace(
    `import { Component, OnInit } from '@angular/core';`,
    `import { Component, OnInit, ChangeDetectorRef } from '@angular/core';`
  );
  
  code = code.replace(
    `    private auth: AuthService`,
    `    private auth: AuthService,
    private cdr: ChangeDetectorRef`
  );
  
  code = code.replace(
    `          this.loading = false;
        },
        error: () => {
          this.loading = false;
        }`,
    `          this.loading = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.loading = false;
          this.cdr.detectChanges();
        }`
  );
  
  fs.writeFileSync(file, code);
  console.log("Patched SuiteDetailComponent");
}
