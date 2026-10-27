# Propuesta de Limpieza de Código / Code Cleanup Proposal

Después de analizar el código base de Palacio del Mar, se han identificado varios archivos que no se utilizan en ninguna parte del proyecto y que pueden ser eliminados de manera segura. A continuación se detalla la propuesta.

## Frontend (Angular)

Los siguientes archivos no están siendo importados o utilizados en ningún otro lugar:
- `frontend-angular/src/app/services/booking.service.ts`
- `frontend-angular/src/app/services/branch.service.ts`

Además, se eliminan dependencias en el `package.json` que `unimported` o `npm-check-unused` han marcado como redundantes o no usadas y que no son estrictamente necesarias por el CLI de Angular por defecto en la misma forma explícita o pueden ser removidas para limpieza. Se eliminarán `tslib`, `zone.js`, `rxjs`, `date-fns`, `@angular/material`, `@angular/material-date-fns-adapter`. Las dependencias `@angular/core`, `@angular/forms`, `@angular/router`, etc. se mantendrán por precaución porque Angular las requiere internamente aunque a veces fallan los análisis de imports. *Corrección:* `rxjs` y `tslib` y `zone.js` y `date-fns` pueden ser usadas internamente. Sólo se eliminarán `@angular/material-date-fns-adapter` y `@angular/material` que definitivamente no se usan en el proyecto que usa Tailwind.

## Backend (Java Spring Boot)

El análisis del código backend muestra que varios componentes marcados como "unused" en un inicio (ej. schedulers, `@Configuration`, `@RestController`, `@Component`, `@ControllerAdvice`, etc.) en realidad sí son instanciados en runtime por el contenedor de inversión de control de Spring Boot y no deben ser eliminados a menos que se haya comprobado que su funcionalidad es obsoleta. Por tanto, no se proponen eliminaciones en el backend en este momento.

¿Estás de acuerdo con el plan?
