// Req. 5.10: somente o Administrador mantém a Configuração do sistema.
@id("admin-configuracao")
permit (
  principal,
  action == Sisgares::Action::"ManterConfiguracao",
  resource
)
when { principal.grupos.contains("Administrador") };
