// Req. 5.9: o Administrador consulta, altera, cancela e vê no painel somente
// Reservas da própria Unidade_Macro.
@id("admin-reservas-unidade")
permit (
  principal,
  action in [
    Sisgares::Action::"ConsultarReserva",
    Sisgares::Action::"AlterarReserva",
    Sisgares::Action::"CancelarReserva",
    Sisgares::Action::"VerPainelAtendente"
  ],
  resource
)
when {
  principal.grupos.contains("Administrador") &&
  resource.unidade == principal.unidade
};
