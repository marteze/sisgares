// Req. 5.10 (salvaguarda): nega explicitamente a manutenção de cadastros e Configuração
// a quem não é Administrador, mesmo que alguma política permit futura conceda acesso.
// Em Cedar, forbid sempre prevalece sobre permit.
@id("proibir-manutencao-nao-admin")
forbid (
  principal,
  action in [
    Sisgares::Action::"ManterCadastro",
    Sisgares::Action::"ManterConfiguracao"
  ],
  resource
)
unless { principal.grupos.contains("Administrador") };
