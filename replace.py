import re

with open('frontend-angular/src/app/pages/suite-detail/suite-detail.component.html', 'r') as f:
    content = f.read()

# Replace everything from <div class="card" style="margin-bottom: 2rem;"> up to auth check
new_step_4 = """
        <!-- Minimal Summary -->
        <div class="card" style="margin-bottom: 2rem; text-align: center;">
          <h3 style="margin-top: 0; color: var(--gold); margin-bottom: 1rem;">Confirmación</h3>
          <p style="color: #ccc; font-size: 1.1rem; margin-bottom: 1.5rem;">Estás a un paso de confirmar tu estadía en <strong>{{suite.name}}</strong>.</p>
          
          <div style="display: flex; justify-content: center; gap: 1rem; margin-bottom: 1.5rem;">
            <button class="btn-primary" style="padding: 6px 16px; font-size: 0.85rem; border: 1px solid var(--gold); background: transparent; color: var(--gold); border-radius: 6px;" (click)="goToStep(1)">✏️ Editar Fechas</button>
            <button class="btn-primary" style="padding: 6px 16px; font-size: 0.85rem; border: 1px solid var(--gold); background: transparent; color: var(--gold); border-radius: 6px;" (click)="goToStep(2)">✏️ Editar Extras</button>
          </div>

          <div style="margin-top: 1.5rem; padding-top: 1.5rem; border-top: 1px solid rgba(212,175,55,0.3); font-size: 1.5rem; font-weight: bold;">
            <span style="color: #fff; margin-right: 15px;">Total Estimado:</span>
            <span style="color: var(--gold);">$ {{grandTotal | number:'1.0-0'}}</span>
          </div>
          <p style="color: #888; font-size: 0.85rem; margin-top: 1rem;">Podrás revisar el desglose detallado en el Centro de Pago.</p>
        </div>

        <!-- Auth Check & Redirect -->
"""

pattern = re.compile(r'<!-- Summary -->.*?<!-- Auth Check & Redirect -->', re.DOTALL)
content = pattern.sub(new_step_4, content)

with open('frontend-angular/src/app/pages/suite-detail/suite-detail.component.html', 'w') as f:
    f.write(content)
