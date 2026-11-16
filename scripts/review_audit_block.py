#!/usr/bin/env python3
"""Submit the current audit diff for read-only Antigravity review, without tool access."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
paths = [
    'backend-java/src/main/java/co/palaciodelmar/backend/service/AuthService.java',
    'backend-java/src/main/java/co/palaciodelmar/backend/security/TokenBlacklistService.java',
    'backend-java/src/main/java/co/palaciodelmar/backend/config/SecurityConfig.java',
    'backend-java/src/main/java/co/palaciodelmar/backend/security/JwtAuthenticationFilter.java',
]
diff = subprocess.check_output(['git', 'diff', '--', 'backend-java', 'frontend-angular/nginx.conf',
    'frontend-angular/security-headers.conf', 'frontend-angular/Dockerfile', '.github/workflows/ci.yml', '.env.example'], cwd=root, text=True)
prompt = '''Revisa profundamente este bloque de cambios de la auditoría Palacio del Mar.
No uses herramientas: tienes el diff y los archivos necesarios debajo. No edites ni ejecutes comandos.
Responde en español con hallazgos, severidad, ruta/línea y corrección sugerida. No cierres el bloque.
Reglas: verificación obligatoria incluso si SMTP falla; reenvío invalida tokens anteriores;
no cambiar contraseña, perfil ni rol de cuentas existentes mediante registro anónimo;
Bearer móvil debe seguir funcionando; túnel https://rehab-perceive-landline.ngrok-free.dev habilitado;
no usar contraseñas reales. Identifica carreras de tokens, regresiones de API y tests insuficientes.
Revisa también CORS/Nginx/seguridad de métodos. La migración de sesiones frontend es un bloque posterior.
DIFF:\n''' + diff
for path in paths:
    prompt += '\nARCHIVO ' + path + '\n' + (root / path).read_text()
output = root / 'docs/antigravity-auth-review.txt'
with output.open('w') as stream:
    result = subprocess.run(['antigravity', '--mode', 'plan', '--print-timeout', '600s', '--print', prompt],
                            cwd=root, stdout=stream, stderr=subprocess.STDOUT, timeout=630)
print(f'Review exit={result.returncode}; {output}')
raise SystemExit(result.returncode)
