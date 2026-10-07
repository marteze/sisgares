import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { gerarPainelAtendenteDemonstracao } from './dados-demonstracao';
import {
  CardReserva,
  ColunaAtendente,
  FiltroPainelAtendente,
  PainelAtendenteService,
} from './painel-atendente.service';

const DIAS_SEMANA = ['domingo', 'segunda-feira', 'terça-feira', 'quarta-feira', 'quinta-feira', 'sexta-feira', 'sábado'];

/** Data corrente no fuso America/Fortaleza, como `yyyy-MM-dd`. */
function hojeFortaleza(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Fortaleza' }).format(new Date());
}

/** Extrai `HH:mm` de um horário ISO (`HH:mm`, `HH:mm:ss` ou data-hora completa). */
function extrairHora(valor: string): string {
  const m = /(\d{2}):(\d{2})/.exec(valor.includes('T') ? valor.split('T')[1] : valor);
  return m ? `${m[1]}:${m[2]}` : valor;
}

interface CardVisao extends CardReserva {
  horaInicio: string;
  horaTermino: string;
}

interface ColunaVisao {
  data: string;
  rotulo: string;
  diaSemana: string;
  cards: CardVisao[];
}

/**
 * Painel do Atendente (Req. 16): uma coluna por data com cards das Reservas,
 * ordenados por horário de início. Cancelamento indicado por texto e cor.
 */
@Component({
  selector: 'app-painel-atendente',
  imports: [RouterLink, MatFormFieldModule, MatSelectModule, MatInputModule, MatCheckboxModule],
  templateUrl: './painel-atendente.html',
  styleUrl: './painel-atendente.scss',
})
export class PainelAtendente {
  private readonly servico = inject(PainelAtendenteService);
  private readonly destroyRef = inject(DestroyRef);
  private consulta?: Subscription;

  protected readonly data = signal(hojeFortaleza());
  protected readonly colunas = signal(7);
  protected readonly fds = signal(false);
  protected readonly painel = signal<ColunaAtendente[]>([]);
  protected readonly carregando = signal(false);
  protected readonly demonstracao = signal(false);
  protected readonly erroFiltro = signal<string | null>(null);
  protected readonly opcoesColunas = Array.from({ length: 14 }, (_, i) => i + 1);

  protected readonly colunasVisao = computed<ColunaVisao[]>(() =>
    this.painel().map((c) => {
      const [a, m, d] = c.data.split('-').map(Number);
      const cards = (c.cards ?? [])
        .map((card) => ({ ...card, horaInicio: extrairHora(card.inicio), horaTermino: extrairHora(card.termino) }))
        .sort((x, y) => x.inicio.localeCompare(y.inicio));
      return {
        data: c.data,
        rotulo: `${String(d).padStart(2, '0')}/${String(m).padStart(2, '0')}/${a}`,
        diaSemana: DIAS_SEMANA[new Date(a, m - 1, d).getDay()],
        cards,
      };
    }),
  );

  /** Mensagem da região `aria-live` com o resultado da consulta. */
  protected readonly mensagemStatus = computed(() => {
    if (this.carregando()) return 'Carregando as reservas…';
    if (this.erroFiltro()) return '';
    const total = this.colunasVisao().reduce((s, c) => s + c.cards.length, 0);
    const base = `${total} reserva(s) em ${this.painel().length} data(s).`;
    return this.demonstracao()
      ? `${base} Atenção: não foi possível acessar o servidor; os dados exibidos são de demonstração.`
      : base;
  });

  constructor() {
    this.carregar();
  }

  protected alterarData(valor: string): void {
    this.data.set(valor);
    this.carregar();
  }

  protected alterarColunas(valor: number): void {
    this.colunas.set(valor);
    this.carregar();
  }

  protected alterarFds(valor: boolean): void {
    this.fds.set(valor);
    this.carregar();
  }

  /** Consulta a API; em caso de falha, usa dados de demonstração em memória. */
  protected carregar(): void {
    const filtro: FiltroPainelAtendente = { data: this.data(), colunas: this.colunas(), fds: this.fds() };
    if (!/^\d{4}-\d{2}-\d{2}$/.test(filtro.data)) {
      this.erroFiltro.set('Informe uma data de referência válida no formato dd/mm/aaaa.');
      return;
    }
    if (filtro.colunas < 1 || filtro.colunas > 14) {
      this.erroFiltro.set('O número de colunas deve estar entre 1 e 14.');
      return;
    }
    this.erroFiltro.set(null);
    this.carregando.set(true);
    this.consulta?.unsubscribe();
    this.consulta = this.servico
      .consultar(filtro)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resp) => {
          this.painel.set(resp.colunas ?? []);
          this.demonstracao.set(false);
          this.carregando.set(false);
        },
        error: () => {
          this.painel.set(gerarPainelAtendenteDemonstracao(filtro).colunas);
          this.demonstracao.set(true);
          this.carregando.set(false);
        },
      });
  }
}
