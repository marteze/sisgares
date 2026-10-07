// Req. 5.5/5.7: qualquer usuário autenticado cria Reserva em seu próprio nome
// e somente na própria Unidade_Macro (impede criar em nome de terceiros ou em outra unidade).
@id("criar-reserva")
permit (
  principal,
  action == Sisgares::Action::"CriarReserva",
  resource
)
when {
  resource.dono == principal &&
  resource.unidade == principal.unidade
};
