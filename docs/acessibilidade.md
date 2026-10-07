# Checklist de acessibilidade

Meta: WCAG 2.1 AA. O que já está implementado no frontend e o que validar na demo.

## Implementado

- HTML semântico: `header`/`nav`/`main`/`footer`, listas e `dl` para metadados.
- Link "Pular para o conteúdo" (`.skip-link`) visível ao receber foco.
- `main` com `id="conteudo"` e `tabIndex=-1` como alvo do skip link.
- Foco visível em todos os interativos (`:focus-visible` com contorno azul).
- Formulário com `label` associado a cada campo, `fieldset`/`legend` para grupos,
  campos obrigatórios com `aria-required`.
- Mensagens de erro com `role="alert"`; estados de carregamento com `role="status"`.
- Ícones decorativos com `alt=""` (não poluem leitores de tela); textos sempre
  acompanham o ícone.
- Filtros do gestor com `role="tab"` / `aria-selected`.
- Seletor de perfil com `label` explícito.
- Contraste da paleta verde do MPF sobre branco/cinza dentro do alvo AA.

## Validar manualmente (importante)

A conformidade total exige teste com tecnologia assistiva e revisão especializada:

- Navegação completa só pelo teclado (Tab/Shift+Tab/Enter/Espaço).
- Leitor de tela (NVDA no Windows) nas telas de criar reserva e aprovar.
- Zoom de 200% sem perda de conteúdo.
- Verificar contraste real com uma ferramenta (ex.: axe DevTools).

> Observação: a validação WCAG completa não é automática — requer teste manual com
> tecnologias assistivas e revisão por especialista em acessibilidade.
