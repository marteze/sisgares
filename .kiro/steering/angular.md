---
inclusion: fileMatch
fileMatchPattern: "frontend/**"
---

# Angular (frontend)

- Somente componentes `standalone`; sem `NgModule`. Prefira signals e `inject()`.
- Acessibilidade conforme eMAG e WCAG 2.1 AA: HTML semântico, `label` associado a cada campo, navegação completa por teclado e foco visível.
- Mensagens dinâmicas (erros, confirmações, carregamento) em região `aria-live` (`polite`; `assertive` só para erros bloqueantes).
- Cor nunca é a única forma de informação: estados da grade e status de reserva também têm texto, ícone ou padrão.
- Contraste mínimo 4,5:1 para texto; imagens com `alt` descritivo ou `alt=""` quando decorativas.
- Textos de interface em português; datas em `dd/MM/yyyy HH:mm`, fuso `America/Fortaleza`.
- Não use `innerHTML` com conteúdo do usuário nem `bypassSecurityTrust*`.
