import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AMBIENTES_RESERVA_DEMO, listarDemo } from './dados-demonstracao';
import { formatarDataHora } from './formatacao';
import { AmbienteCatalogo, Reserva, ReservasService, TEXTO_STATUS } from './reservas.service';

/** Lista das reservas do Solicitante (`/reservas`). */
@Component({
  selector: 'app-lista-reservas',
  imports: [RouterLink, MatButtonModule],
  template: `
    <section class="reservas" aria-labelledby="titulo-pagina">
      <div class="topo">
        <h2 id="titulo-pagina" tabindex="-1">Minhas reservas</h2>
        <a mat-flat-button routerLink="/reservas/nova">Nova reserva</a>
      </div>
      <p class="status" [class.aviso-demo]="demonstracao()" role="status" aria-live="polite">{{ mensagem() }}</p>
      @if (reservas().length) {
        <div class="tabela-rolagem" tabindex="0" role="region" aria-label="Lista de reservas">
          <table>
            <caption>Reservas cadastradas</caption>
            <thead>
              <tr>
                <th scope="col">Reserva</th>
                <th scope="col">Ambiente</th>
                <th scope="col">Primeiro período</th>
                <th scope="col">Finalidade</th>
                <th scope="col">Status</th>
              </tr>
            </thead>
            <tbody>
              @for (r of reservas(); track r.id) {
                <tr>
                  <td><a [routerLink]="['/reservas', r.id]">Abrir reserva {{ r.id }}</a></td>
                  <td>{{ nomeAmbiente(r.ambienteId) }}</td>
                  <td>{{ formatar(r.periodos[0]?.inicio) }}</td>
                  <td>{{ r.finalidade }}</td>
                  <td>{{ r.status ? textoStatus[r.status] : '' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  styles: `
    .reservas { padding: 1rem; min-width: 0; }
    .topo { display: flex; flex-wrap: wrap; gap: 1rem; align-items: center; justify-content: space-between; }
    .status { min-height: 1.5em; }
    .aviso-demo { padding: .5rem .75rem; border-left: 4px solid #8a5a00; background: #fff4dc; color: #4a3000; }
    .tabela-rolagem { max-width: 100%; overflow-x: auto; }
    .tabela-rolagem:focus-visible, a:focus-visible { outline: 3px solid #1a4fa0; outline-offset: 2px; }
    table { border-collapse: collapse; width: 100%; }
    caption { text-align: left; font-weight: 600; padding: .5rem 0; }
    th, td { border: 1px solid #8c8c8c; padding: .25rem .5rem; text-align: left; }
    thead th { background: #e8eef7; color: #102040; }
    td a { color: #0b3d91; font-weight: 600; }
  `,
})
export class ListaReservas {
  private readonly servico = inject(ReservasService);
  protected readonly reservas = signal<Reserva[]>([]);
  protected readonly ambientes = signal<AmbienteCatalogo[]>([]);
  protected readonly carregando = signal(true);
  protected readonly demonstracao = signal(false);
  protected readonly textoStatus = TEXTO_STATUS;
  protected readonly formatar = formatarDataHora;

  protected readonly mensagem = computed(() => {
    if (this.carregando()) return 'Carregando reservas…';
    const base = this.reservas().length
      ? `${this.reservas().length} reserva(s) encontrada(s).`
      : 'Nenhuma reserva encontrada.';
    return this.demonstracao()
      ? `${base} Atenção: não foi possível acessar o servidor; os dados exibidos são de demonstração.`
      : base;
  });

  constructor() {
    forkJoin({ reservas: this.servico.listar(), ambientes: this.servico.ambientes() })
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe({
        next: ({ reservas, ambientes }) => {
          this.reservas.set(reservas);
          this.ambientes.set(ambientes);
          this.carregando.set(false);
        },
        error: () => {
          this.reservas.set(listarDemo());
          this.ambientes.set(AMBIENTES_RESERVA_DEMO);
          this.demonstracao.set(true);
          this.carregando.set(false);
        },
      });
  }

  protected nomeAmbiente(id: string | null): string {
    if (!id) return 'Não solicitado / local próprio';
    return this.ambientes().find((a) => a.id === id)?.nome ?? id;
  }
}
