import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { formatarDataHora } from '../reservas/formatacao';
import { CaixaSimuladaService, NOTIFICACOES_DEMO, NotificacaoSimulada } from './caixa-simulada.service';

const TEXTO_TIPO: Record<NotificacaoSimulada['tipo'], string> = {
  CRIACAO: 'Criação',
  ALTERACAO: 'Alteração',
  CANCELAMENTO: 'Cancelamento',
};

/**
 * Caixa simulada (`/caixa-simulada`, somente Administrador): e-mails de notificação
 * com destaque das alterações em `<del>`/`<ins>` (Req. 13.6). Todo conteúdo é
 * renderizado por binding de texto; nunca por `innerHTML`.
 */
@Component({
  selector: 'app-caixa-simulada',
  template: `
    <section class="caixa" aria-labelledby="titulo-pagina">
      <h2 id="titulo-pagina" tabindex="-1">Caixa simulada</h2>
      <p class="status" [class.aviso-demo]="demonstracao()" role="status" aria-live="polite">{{ mensagemStatus() }}</p>
      @if (!carregando()) {
        <ul class="emails">
          @for (n of notificacoes(); track n.id) {
            <li>
              <article class="email" [attr.aria-labelledby]="'assunto-' + n.id">
                <h3 [id]="'assunto-' + n.id">{{ n.assunto }}</h3>
                <dl class="cabecalho">
                  <div><dt>Para</dt><dd>{{ n.destinatario }}</dd></div>
                  <div><dt>Enviado em</dt><dd>{{ formatar(n.enviadaEm) }}</dd></div>
                  <div><dt>Tipo</dt><dd>{{ textoTipo[n.tipo] }}</dd></div>
                  <div><dt>Reserva</dt><dd>{{ n.reservaId }}</dd></div>
                </dl>
                <p>{{ n.resumo }}</p>
                @if (n.alteracoes.length) {
                  <table>
                    <caption>Alterações da reserva {{ n.reservaId }}</caption>
                    <thead>
                      <tr><th scope="col">Campo</th><th scope="col">Valor anterior</th><th scope="col">Novo valor</th></tr>
                    </thead>
                    <tbody>
                      @for (a of n.alteracoes; track a.campo) {
                        <tr>
                          <th scope="row">{{ a.campo }}</th>
                          <td>
                            @if (a.anterior) { <del><span class="sr-only">Removido: </span>{{ a.anterior }}</del> } @else { — }
                          </td>
                          <td>
                            @if (a.novo) { <ins><span class="sr-only">Incluído: </span>{{ a.novo }}</ins> } @else { — }
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                }
              </article>
            </li>
          } @empty {
            <li>Nenhuma notificação enviada.</li>
          }
        </ul>
      }
    </section>
  `,
  styles: `
    .caixa { padding: 1rem; }
    .status { min-height: 1.5em; }
    .aviso-demo { padding: .5rem .75rem; border-left: 4px solid #8a5a00; background: #fff4dc; color: #4a3000; }
    .emails { list-style: none; padding: 0; display: grid; gap: 1rem; }
    .email { border: 1px solid #6b6b6b; border-radius: 4px; padding: .75rem 1rem; }
    .email h3 { margin-top: 0; }
    .cabecalho div { display: flex; gap: .5rem; }
    .cabecalho dt { font-weight: 600; }
    .cabecalho dd { margin: 0; }
    table { border-collapse: collapse; width: 100%; }
    caption { text-align: left; font-weight: 600; margin-bottom: .25rem; }
    th, td { border: 1px solid #8a8a8a; padding: .25rem .5rem; text-align: left; }
    del { background: #fde2e2; color: #6b0000; }
    ins { background: #dcf3dc; color: #0b4a0b; text-decoration: underline; }
  `,
})
export class CaixaSimulada {
  private readonly servico = inject(CaixaSimuladaService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly textoTipo = TEXTO_TIPO;
  protected readonly formatar = formatarDataHora;
  protected readonly notificacoes = signal<NotificacaoSimulada[]>([]);
  protected readonly carregando = signal(true);
  protected readonly demonstracao = signal(false);

  protected readonly mensagemStatus = computed(() => {
    if (this.carregando()) return 'Carregando notificações…';
    const base = `${this.notificacoes().length} notificação(ões).`;
    return this.demonstracao()
      ? `${base} Atenção: dados de demonstração com e-mails fictícios; o servidor ainda não disponibiliza a caixa simulada.`
      : base;
  });

  constructor() {
    this.servico
      .listar()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (lista) => {
          this.notificacoes.set(lista ?? []);
          this.carregando.set(false);
        },
        error: () => {
          this.notificacoes.set(NOTIFICACOES_DEMO);
          this.demonstracao.set(true);
          this.carregando.set(false);
        },
      });
  }
}
