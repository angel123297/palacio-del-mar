const fs = require('fs');

const file = 'frontend-angular/src/app/components/rooms-section/rooms-section.component.ts';
let code = fs.readFileSync(file, 'utf8');

// Add ChangeDetectorRef import
code = code.replace(
  `import { Component, OnInit } from '@angular/core';`,
  `import { Component, OnInit, ChangeDetectorRef } from '@angular/core';`
);

// Add to constructor
code = code.replace(
  `  constructor(private api: ApiService) {}`,
  `  constructor(private api: ApiService, private cdr: ChangeDetectorRef) {}`
);

// Add detectChanges to next/error
code = code.replace(
  `      next: (res) => {
        const raw = res.data || res;
        const list = Array.isArray(raw) ? raw : (raw.suites || []);
        this.suites = list;
        this.loading = false;
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
      }`,
  `      next: (res) => {
        const raw = res.data || res;
        const list = Array.isArray(raw) ? raw : (raw.suites || []);
        this.suites = list;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loadError = true;
        this.loading = false;
        this.cdr.detectChanges();
      }`
);

// Branches and types
code = code.replace(
  `        this.branches = res.data || res;`,
  `        this.branches = res.data || res;
        this.cdr.detectChanges();`
);
code = code.replace(
  `        this.types = res.data || res;`,
  `        this.types = res.data || res;
        this.cdr.detectChanges();`
);

fs.writeFileSync(file, code);
console.log("Patched RoomsSectionComponent");
