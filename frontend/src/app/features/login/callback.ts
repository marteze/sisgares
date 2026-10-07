import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

/** Rota de retorno do Cognito Hosted UI: troca o código de autorização (PKCE) por tokens. */
@Component({
  selector: 'app-callback',
  imports: [RouterLink],
  template: `
    <section class="callback" aria-labelledby="titulo-callback">
      <h2 id="titulo-callback">Concluindo login</h2>
      @if (erro(); as mensagem) {
        <p role="alert">{{ mensagem }}</p>
        <a routerLink="/login">Voltar ao login</a>
      } @else {
        <p role="status" aria-live="polite">Aguarde, validando seu acesso…</p>
      }
    </section>
  `,
  styles: `
    .callback { padding: 1rem; }
  `,
})
export class Callback implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly rota = inject(ActivatedRoute);
  protected readonly erro = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    const params = this.rota.snapshot.queryParamMap;
    if (params.get('error')) {
      this.erro.set('O login foi cancelado ou recusado. Tente entrar novamente.');
      return;
    }
    try {
      await this.auth.concluirLoginCognito(params.get('code'), params.get('state'));
    } catch (e) {
      this.erro.set(e instanceof Error && e.message.startsWith('Não foi')
        ? e.message
        : 'Não foi possível concluir o login. Tente entrar novamente.');
    }
  }
}
