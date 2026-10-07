import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

export interface DadosDialogoConfirmacao {
  titulo: string;
  descricao: string;
  confirmar: string;
}

/**
 * Diálogo genérico de confirmação (`role="alertdialog"` definido na abertura).
 * O foco inicial fica em "Voltar"; Esc fecha sem confirmar.
 */
@Component({
  selector: 'app-dialogo-confirmacao',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title id="titulo-dialogo-confirmacao">{{ dados.titulo }}</h2>
    <mat-dialog-content>
      <p id="descricao-dialogo-confirmacao">{{ dados.descricao }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" id="botao-voltar-confirmacao" [mat-dialog-close]="false">Voltar</button>
      <button mat-flat-button type="button" [mat-dialog-close]="true">{{ dados.confirmar }}</button>
    </mat-dialog-actions>
  `,
})
export class DialogoConfirmacao {
  protected readonly dados = inject<DadosDialogoConfirmacao>(MAT_DIALOG_DATA);
}
