// Req. 5.9/5.10: somente o Administrador mantém cadastros, e apenas os da própria Unidade_Macro.
@id("admin-cadastros-unidade")
permit (
  principal,
  action == Sisgares::Action::"ManterCadastro",
  resource
)
when {
  principal.grupos.contains("Administrador") &&
  resource.unidade == principal.unidade
};
