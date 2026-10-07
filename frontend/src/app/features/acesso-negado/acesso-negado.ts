import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../core/auth/auth.service';

/** Exibida quando o perfil do usuário não permite acessar a rota solicitada. */
@Component({
  selector: 'app-acesso-negado',
  imports: [MatButtonModule],
  template: `
    <section class="acesso-negado" aria-labelledby="titulo-pagina">
      <h2 id="titulo-pagina">Acesso negado</h2>
      <p>Seu perfil não tem permissão para acessar esta página. Use o menu para ir a uma tela permitida.</p>
      <button mat-stroked-button type="button" (click)="sair()">Entrar com outro usuário</button>
    </section>
  `,
  styles: `
    .acesso-negado { padding: 1rem; }
  `,
})
export class AcessoNegado {
  private readonly auth = inject(AuthService);

  protected sair(): void {
    this.auth.sair();
  }
}
