// Req. 5.7: o dono (Solicitante) consulta, altera e cancela somente as próprias Reservas.
// Nenhum outro Solicitante recebe essas permissões (negação por padrão do Cedar).
@id("dono-reserva")
permit (
  principal,
  action in [
    Sisgares::Action::"ConsultarReserva",
    Sisgares::Action::"AlterarReserva",
    Sisgares::Action::"CancelarReserva"
  ],
  resource
)
when { resource.dono == principal };
