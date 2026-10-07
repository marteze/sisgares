// Req. 5.8: o Setor_Atendente consulta (inclusive pelo painel do Atendente) somente
// Reservas cujo Ambiente ou Recursos estejam vinculados ao seu Setor_Envolvido,
// dentro da própria Unidade_Macro. Atendente sem setor não recebe acesso.
@id("atendente-setor")
permit (
  principal,
  action in [
    Sisgares::Action::"ConsultarReserva",
    Sisgares::Action::"VerPainelAtendente"
  ],
  resource
)
when {
  principal.grupos.contains("Setor_Atendente") &&
  principal has setor &&
  resource.setores.contains(principal.setor) &&
  resource.unidade == principal.unidade
};
