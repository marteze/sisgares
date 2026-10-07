import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

/** Dados exibidos no diálogo de confirmação de cancelamento. */
export interface DadosDialogoCancelamento {
  reservaId: string;
}

/**
 * Diálogo acessível de confirmação de cancelamento (Req. 12.5).
 * `role="alertdialog"`, `aria-labelledby` e `aria-describedby` são definidos na abertura
 * (MatDialogConfig); o foco inicial vai para "Voltar" e Esc fecha sem cancelar.
 */
@Component({
  selector: 'app-dialogo-cancelamento',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title id="titulo-dialogo-cancelamento">Cancelar a reserva {{ dados.reservaId }}?</h2>
    <mat-dialog-content>
      <p id="descricao-dialogo-cancelamento">
        O cancelamento libera o ambiente e os recursos e não pode ser desfeito.
        Escolha "Confirmar cancelamento" para prosseguir ou "Voltar" para manter a reserva.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" id="botao-voltar-cancelamento" [mat-dialog-close]="false">Voltar</button>
      <button mat-flat-button type="button" class="botao-perigo" [mat-dialog-close]="true">Confirmar cancelamento</button>
    </mat-dialog-actions>
  `,
})
export class DialogoCancelamento {
  protected readonly dados = inject<DadosDialogoCancelamento>(MAT_DIALOG_DATA);
}
