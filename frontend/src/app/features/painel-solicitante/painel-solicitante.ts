import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AMBIENTES_DEMONSTRACAO, gerarPainelDemonstracao } from './dados-demonstracao';
import {
  AmbienteResumo,
  CelulaPainel,
  ColunaPainel,
  EstadoCelula,
  FiltroPainel,
  PainelSolicitanteService,
} from './painel-solicitante.service';

/** Texto exibido para cada estado; o estado nunca é indicado só por cor (Req. 20). */
const TEXTO_ESTADO: Record<Exclude<EstadoCelula, 'LIVRE'>, string> = {
  OCUPADO: 'Reservado',
  MARGEM: 'Margem de tolerância',
  ULTRAPASSADO: 'Horário ultrapassado',
  SEM_ANTECEDENCIA: 'Sem antecedência mínima',
};

const DIAS_SEMANA = ['domingo', 'segunda-feira', 'terça-feira', 'quarta-feira', 'quinta-feira', 'sexta-feira', 'sábado'];

/** Data corrente no fuso America/Fortaleza, como `yyyy-MM-dd`. */
function hojeFortaleza(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Fortaleza' }).format(new Date());
}

/** Extrai `HH:mm` de um `inicio` ISO (`HH:mm`, `HH:mm:ss` ou data-hora completa). */
function extrairHora(inicio: string): string {
  const m = /(\d{2}):(\d{2})/.exec(inicio.includes('T') ? inicio.split('T')[1] : inicio);
  return m ? `${m[1]}:${m[2]}` : inicio;
}

interface CelulaVisao {
  estado: EstadoCelula | 'INDEFINIDO';
  texto: string;
  reservaId?: string;
}

interface ColunaVisao {
  data: string;
  rotulo: string;
  diaSemana: string;
  celulas: Map<string, CelulaPainel>;
}

/**
 * Painel do Solicitante (Req. 15): grade de datas × horários de 30 minutos
 * para o Ambiente selecionado. Os estados chegam calculados pelo backend.
 */
@Component({
  selector: 'app-painel-solicitante',
  imports: [FormsModule, RouterLink, MatFormFieldModule, MatSelectModule, MatInputModule, MatCheckboxModule],
  templateUrl: './painel-solicitante.html',
  styleUrl: './painel-solicitante.scss',
})
export class PainelSolicitante {
  private readonly servico = inject(PainelSolicitanteService);
  private readonly destroyRef = inject(DestroyRef);
  private consulta?: Subscription;

  protected readonly ambientes = signal<AmbienteResumo[]>([]);
  protected readonly ambiente = signal('');
  protected readonly data = signal(hojeFortaleza());
  protected readonly colunas = signal(7);
  protected readonly fds = signal(false);

  protected readonly grade = signal<ColunaPainel[]>([]);
  protected readonly carregando = signal(false);
  protected readonly demonstracao = signal(false);
  protected readonly erroFiltro = signal<string | null>(null);

  protected readonly opcoesColunas = Array.from({ length: 14 }, (_, i) => i + 1);

  protected readonly nomeAmbiente = computed(
    () => this.ambientes().find((a) => a.id === this.ambiente())?.nome ?? '',
  );

  protected readonly colunasVisao = computed<ColunaVisao[]>(() =>
    this.grade().map((c) => {
      const [a, m, d] = c.data.split('-').map(Number);
      return {
        data: c.data,
        rotulo: `${String(d).padStart(2, '0')}/${String(m).padStart(2, '0')}/${a}`,
        diaSemana: DIAS_SEMANA[new Date(a, m - 1, d).getDay()],
        celulas: new Map(c.celulas.map((cel) => [extrairHora(cel.inicio), cel])),
      };
    }),
  );

  /** Linhas a cada 30 minutos: união dos horários de todas as colunas (Req. 15.3). */
  protected readonly horarios = computed(() => {
    const todos = new Set<string>();
    for (const c of this.colunasVisao()) c.celulas.forEach((_, h) => todos.add(h));
    return [...todos].sort();
  });

  /** Mensagem da região `aria-live` com o resultado da consulta. */
  protected readonly mensagemStatus = computed(() => {
    if (this.carregando()) return 'Carregando a grade de horários…';
    if (this.erroFiltro()) return '';
    if (!this.ambiente()) return '';
    const base = `Grade de ${this.nomeAmbiente()} com ${this.grade().length} data(s).`;
    return this.demonstracao()
      ? `${base} Atenção: não foi possível acessar o servidor; os dados exibidos são de demonstração.`
      : base;
  });

  constructor() {
    this.servico
      .listarAmbientes()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (lista) => this.iniciarAmbientes(lista, false),
        error: () => this.iniciarAmbientes(AMBIENTES_DEMONSTRACAO, true),
      });
  }

  private iniciarAmbientes(lista: AmbienteResumo[], demonstracao: boolean): void {
    this.ambientes.set(lista);
    if (demonstracao) this.demonstracao.set(true);
    if (lista.length && !this.ambiente()) this.ambiente.set(lista[0].id);
    this.carregar();
  }

  protected alterarAmbiente(id: string): void {
    this.ambiente.set(id);
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
    const filtro: FiltroPainel = {
      ambiente: this.ambiente(),
      data: this.data(),
      colunas: this.colunas(),
      fds: this.fds(),
    };
    if (!filtro.ambiente) return;
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
          this.grade.set(resp.colunas ?? []);
          this.demonstracao.set(false);
          this.carregando.set(false);
        },
        error: () => {
          this.grade.set(gerarPainelDemonstracao(filtro).colunas);
          this.demonstracao.set(true);
          this.carregando.set(false);
        },
      });
  }

  protected celula(coluna: ColunaVisao, horario: string): CelulaVisao {
    const cel = coluna.celulas.get(horario);
    if (!cel) return { estado: 'INDEFINIDO', texto: 'Fora da faixa horária' };
    if (cel.estado === 'LIVRE') return { estado: 'LIVRE', texto: `Reservar às ${horario}` };
    return { estado: cel.estado, texto: TEXTO_ESTADO[cel.estado], reservaId: cel.reservaId };
  }

  /** Query params do cadastro com Ambiente, data e início preenchidos (Req. 15.10). */
  protected paramsReserva(coluna: ColunaVisao, horario: string): Record<string, string> {
    return { ambiente: this.ambiente(), data: coluna.data, inicio: horario };
  }
}
