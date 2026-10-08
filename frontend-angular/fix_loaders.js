const fs = require('fs');

const files = [
  'src/app/components/rooms-section/rooms-section.component.ts',
  'src/app/pages/admin-dashboard/admin-dashboard.component.ts',
  'src/app/pages/my-bookings/my-bookings.component.ts',
  'src/app/pages/suite-detail/suite-detail.component.ts'
];

for (const file of files) {
  let content = fs.readFileSync(file, 'utf8');
  
  content = content.replace(
    /<div \*ngIf="loading" style="text-align: center;[^>]*>[\s\S]*?<\/div>/,
    `<div *ngIf="loading" class="luxury-loader-container">
        <div class="luxury-spinner"></div>
        <p class="luxury-loader-text">Cargando...</p>
      </div>`
  );
  
  fs.writeFileSync(file, content);
}
console.log("Loaders updated.");
