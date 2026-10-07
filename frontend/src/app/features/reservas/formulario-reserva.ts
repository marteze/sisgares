import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormArray, FormControl, FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, catchError, forkJoin, map, of, startWith, timeout } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import {
  AMBIENTES_RESERVA_DEMO,
  DISPOSICOES_DEMO,
  RECURSOS_DEMO,
  cancelarDemo,
  obterDemo,
  salvarDemo,
  verificarDemo,
} from './dados-demonstracao';
import { DadosDialogoCancelamento, DialogoCancelamento } from './dialogo-cancelamento';
import { formatarDataHora } from './formatacao';
import {
  AmbienteCatalogo,
  Conflito,
  DisposicaoCatalogo,
  ErroApi,
  PeriodoReserva,
  RecursoCatalogo,
  Reserva,
  ReservasService,
  TEXTO_STATUS,
} from './reservas.service';

/** Valor do campo Ambiente que representa Local_Proprio (sem Ambiente). */
const LOCAL_PROPRIO = '';
const PASTA_DISPOSICAO = '/imagens/icones-disposicao/';
const IMAGEM_PADRAO_DISPOSICAO = 'indefinido_disposicao-nao-definida.jpg';

type GrupoPeriodo = FormGroup<{ inicio: FormControl<string>; termino: FormControl<string> }>;
type GrupoRecurso = FormGroup<{ recursoId: FormControl<string>; quantidade: FormControl<number | null> }>;

/** Resultado da verificação de conflito de um Período (Req. 10.5). */
interface EstadoPeriodo {
  verificando: boolean;
  conflitos: Conflito[];
  falha?: string;
}

interface GrupoRecursosVisao {
  nome: string;
  ordem: number;
  recursos: RecursoCatalogo[];
}

/** Soma minutos a uma data-hora local `yyyy-MM-ddTHH:mm`. */
function somarMinutos(dataHora: string, minutos: number): string {
  const [d, h] = dataHora.split('T');
  const [a, m, dia] = d.split('-').map(Number);
  const [hh, mm] = h.split(':').map(Number);
  const r = new Date(a, m - 1, dia, hh, mm + minutos);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${r.getFullYear()}-${p(r.getMonth() + 1)}-${p(r.getDate())}T${p(r.getHours())}:${p(r.getMinutes())}`;
}

/** Normaliza data-hora ISO para o formato do `input datetime-local`. */
function paraCampo(iso: string): string {
  const m = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/.exec(iso);
  return m ? `${m[1]}T${m[2]}` : '';
}

/**
 * Cadastro e edição de Reserva (`/reservas/nova` e `/reservas/:id`).
 * Requisitos 9.1–9.3, 10.5, 11.1, 11.8, 14.6, 20.5, 20.8 e 20.9.
 */
@Component({
  selector: 'app-formulario-reserva',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
  ],
  templateUrl: './formulario-reserva.html',
  styleUrl: './formulario-reserva.scss',
})
export class FormularioReserva {
  private readonly servico = inject(ReservasService);
  private readonly auth = inject(AuthService);
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly dialogo = inject(MatDialog);

  protected readonly LOCAL_PROPRIO = LOCAL_PROPRIO;
  protected readonly textoStatus = TEXTO_STATUS;
  protected readonly formatar = formatarDataHora;

  /** Id da Reserva em edição; `null` no cadastro. */
  protected readonly reservaId = this.rota.snapshot.paramMap.get('id');
  protected readonly reserva = signal<Reserva | null>(null);
  protected readonly ambientes = signal<AmbienteCatalogo[]>([]);
  protected readonly recursosCatalogo = signal<RecursoCatalogo[]>([]);
  protected readonly disposicoes = signal<DisposicaoCatalogo[]>([]);
  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly cancelando = signal(false);
  protected readonly demonstracao = signal(false);
  protected readonly naoEncontrada = signal(false);
  protected readonly errosApi = signal<ErroApi[]>([]);
  /** Mensagens informativas (avisos de recursos removidos, salvamento etc.). */
  protected readonly aviso = signal<string>((history.state as { mensagem?: string } | null)?.mensagem ?? '');
  protected readonly estadosPeriodo = signal(new Map<GrupoPeriodo, EstadoPeriodo>());

  protected readonly form = this.fb.group({
    ambienteId: this.fb.control<string>(LOCAL_PROPRIO),
    finalidade: this.fb.control('', [Validators.required, Validators.pattern(/\S/)]),
    participantes: new FormControl<number | null>(null, [Validators.required, Validators.min(1)]),
    disposicaoId: new FormControl<string | null>(null),
    complemento: this.fb.control(''),
    periodos: this.fb.array<GrupoPeriodo>([]),
    recursos: this.fb.array<GrupoRecurso>([]),
  });

  private readonly ambienteAtual = toSignal(
    this.form.controls.ambienteId.valueChanges.pipe(startWith(this.form.controls.ambienteId.value)),
    { initialValue: LOCAL_PROPRIO },
  );
  private readonly disposicaoAtual = toSignal(
    this.form.controls.disposicaoId.valueChanges.pipe(startWith(this.form.controls.disposicaoId.value)),
    { initialValue: null },
  );

  protected readonly localProprio = computed(() => this.ambienteAtual() === LOCAL_PROPRIO);
  protected readonly ambientesAtivos = computed(() => this.ambientes().filter((a) => a.ativo !== false));

  /** Disposição selecionada com imagem e texto alternativo (Req. 9.2, 20.8, 20.9). */
  protected readonly disposicaoSelecionada = computed(() => {
    const d = this.disposicoes().find((x) => x.id === this.disposicaoAtual());
    if (!d) return null;
    return {
      descricao: d.descricao,
      src: PASTA_DISPOSICAO + encodeURIComponent(d.imagem || IMAGEM_PADRAO_DISPOSICAO),
      alt: d.alt?.trim() || d.descricao,
    };
  });

  /** Recursos permitidos no Ambiente atual, agrupados por GREC_ORDEM (Req. 11.5, 11.6). */
  protected readonly gruposRecursos = computed<GrupoRecursosVisao[]>(() => {
    const amb = this.ambienteAtual();
    const grupos = new Map<string, GrupoRecursosVisao>();
    for (const r of this.recursosCatalogo().filter((x) => this.permitido(x, amb))) {
      const chave = r.grupoId ?? 'SEM_GRUPO';
      if (!grupos.has(chave)) {
        grupos.set(chave, { nome: r.grupoNome ?? 'Outros recursos', ordem: r.grupoOrdem ?? Number.MAX_SAFE_INTEGER, recursos: [] });
      }
      grupos.get(chave)!.recursos.push(r);
    }
    return [...grupos.values()]
      .sort((a, b) => a.ordem - b.ordem || a.nome.localeCompare(b.nome, 'pt-BR'))
      .map((g) => ({ ...g, recursos: g.recursos.sort((a, b) => a.descricao.localeCompare(b.descricao, 'pt-BR')) }));
  });

  /** Botão "Cancelar reserva" só na edição e fora de TRANSCORRIDA/CANCELADA (Req. 12.5). */
  protected readonly podeCancelar = computed(() => {
    const st = this.reserva()?.status;
    return !!this.reservaId && !!this.reserva() && st !== 'TRANSCORRIDA' && st !== 'CANCELADA';
  });

  /** Erros sem campo correspondente na tela, exibidos no resumo. */
  protected readonly errosGerais = computed(() =>
    this.errosApi().filter((e) => !e.campo || !this.campoConhecido(e.campo)),
  );

  /** Texto da região `aria-live` assertiva para erros bloqueantes (Req. 20.5). */
  protected readonly resumoErros = computed(() => {
    const n = this.errosApi().length;
    if (!n) return '';
    return `${this.tituloErros()}: ${n} erro(s). ` + this.errosApi().map((e) => e.mensagem).join(' ');
  });

  /** Origem dos erros exibidos no resumo (salvamento ou cancelamento). */
  protected readonly operacaoErro = signal<'salvar' | 'cancelar'>('salvar');
  protected readonly tituloErros = computed(() =>
    this.operacaoErro() === 'cancelar' ? 'A reserva não foi cancelada' : 'A reserva não foi salva',
  );

  protected readonly mensagemStatus = computed(() => {
    if (this.carregando()) return 'Carregando formulário de reserva…';
    if (this.salvando()) return 'Salvando reserva…';
    if (this.cancelando()) return 'Cancelando reserva…';
    const demo = this.demonstracao()
      ? 'Atenção: não foi possível acessar o servidor; os dados exibidos são de demonstração e ficam apenas em memória.'
      : '';
    return [this.aviso(), demo].filter(Boolean).join(' ');
  });

  constructor() {
    this.carregar();

    // Ao trocar o Ambiente: remove Recursos não permitidos e Disposição em Local_Proprio (Req. 11.8, 9.2).
    this.form.controls.ambienteId.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((amb) => {
      if (amb === LOCAL_PROPRIO) this.form.controls.disposicaoId.setValue(null);
      this.filtrarRecursos(amb);
      this.form.controls.periodos.controls.forEach((p) => this.verificarPeriodo(p));
    });
  }

  private carregar(): void {
    const catalogo$ = forkJoin({
      ambientes: this.servico.ambientes(),
      recursos: this.servico.recursos(),
      disposicoes: this.servico.disposicoes(),
    }).pipe(
      map((c) => ({ ...c, demo: false })),
      catchError(() =>
        of({ ambientes: AMBIENTES_RESERVA_DEMO, recursos: RECURSOS_DEMO, disposicoes: DISPOSICOES_DEMO, demo: true }),
      ),
    );
    const reserva$: Observable<{ reserva: Reserva | null; demo: boolean }> = this.reservaId
      ? this.servico.obter(this.reservaId).pipe(
          map((r) => ({ reserva: r, demo: false })),
          catchError((e: HttpErrorResponse) =>
            of({ reserva: obterDemo(this.reservaId!) ?? null, demo: e.status !== 404 && e.status !== 403 }),
          ),
        )
      : of({ reserva: null, demo: false });

    forkJoin({ catalogo: catalogo$, dados: reserva$ })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ catalogo, dados }) => {
        this.ambientes.set(catalogo.ambientes);
        this.recursosCatalogo.set(catalogo.recursos);
        this.disposicoes.set(catalogo.disposicoes);
        this.demonstracao.set(catalogo.demo || dados.demo);
        if (this.reservaId) {
          if (dados.reserva) this.preencher(dados.reserva);
          else this.naoEncontrada.set(true);
        } else {
          this.preencherPorQueryParams();
        }
        this.carregando.set(false);
      });
  }

  /** Lê `ambiente`, `data` e `inicio` vindos do Painel (Req. 15.10). */
  private preencherPorQueryParams(): void {
    const q = this.rota.snapshot.queryParamMap;
    const amb = q.get('ambiente');
    if (amb && this.ambientesAtivos().some((a) => a.id === amb)) {
      this.form.controls.ambienteId.setValue(amb, { emitEvent: false });
    }
    const data = q.get('data');
    const inicio = q.get('inicio');
    if (data && /^\d{4}-\d{2}-\d{2}$/.test(data)) {
      const ini = `${data}T${inicio && /^\d{2}:\d{2}$/.test(inicio) ? inicio : '08:00'}`;
      this.adicionarPeriodo({ inicio: ini, termino: somarMinutos(ini, 60) });
    } else {
      this.adicionarPeriodo();
    }
  }

  private preencher(r: Reserva): void {
    this.reserva.set(r);
    this.form.patchValue(
      {
        ambienteId: r.ambienteId ?? LOCAL_PROPRIO,
        finalidade: r.finalidade,
        participantes: r.participantes,
        disposicaoId: r.disposicaoId ?? null,
        complemento: r.complemento ?? '',
      },
      { emitEvent: false },
    );
    // Reemite valores para atualizar os signals derivados sem disparar a limpeza de recursos.
    this.form.controls.disposicaoId.setValue(r.disposicaoId ?? null);
    const periodos = this.form.controls.periodos;
    periodos.clear();
    (r.periodos.length ? r.periodos : [{ inicio: '', termino: '' }]).forEach((p) =>
      this.adicionarPeriodo({ inicio: paraCampo(p.inicio), termino: paraCampo(p.termino) }, false),
    );
    const recursos = this.form.controls.recursos;
    recursos.clear();
    r.recursos.forEach((s) => recursos.push(this.novoRecurso(s.recursoId, s.quantidade ?? null)));
    // Atualiza o signal do Ambiente sem passar pelo filtro (valores vieram do backend).
    this.ambienteSemFiltro = true;
    this.form.controls.ambienteId.setValue(r.ambienteId ?? LOCAL_PROPRIO);
    this.ambienteSemFiltro = false;
  }

  private ambienteSemFiltro = false;

  // ---------- Períodos ----------

  protected get periodos(): FormArray<GrupoPeriodo> {
    return this.form.controls.periodos;
  }

  protected adicionarPeriodo(valor: PeriodoReserva = { inicio: '', termino: '' }, verificar = true): void {
    const g: GrupoPeriodo = this.fb.group({
      inicio: this.fb.control(valor.inicio, Validators.required),
      termino: this.fb.control(valor.termino, Validators.required),
    });
    this.periodos.push(g);
    if (verificar && valor.inicio && valor.termino) this.verificarPeriodo(g);
  }

  protected removerPeriodo(i: number): void {
    const g = this.periodos.at(i);
    this.periodos.removeAt(i);
    this.estadosPeriodo.update((m) => {
      const n = new Map(m);
      n.delete(g);
      return n;
    });
    this.aviso.set(`Período ${i + 1} removido.`);
  }

  protected estadoPeriodo(g: GrupoPeriodo): EstadoPeriodo | undefined {
    return this.estadosPeriodo().get(g);
  }

  private definirEstado(g: GrupoPeriodo, e: EstadoPeriodo | null): void {
    this.estadosPeriodo.update((m) => {
      const n = new Map(m);
      if (e) n.set(g, e);
      else n.delete(g);
      return n;
    });
  }

  /** Consulta conflitos assim que o Período está completo (Req. 10.5). Local_Proprio não verifica (Req. 10.4). */
  protected verificarPeriodo(g: GrupoPeriodo): void {
    const { inicio, termino } = g.getRawValue();
    const amb = this.form.controls.ambienteId.value;
    if (!inicio || !termino || amb === LOCAL_PROPRIO) {
      this.definirEstado(g, null);
      return;
    }
    if (termino <= inicio) {
      this.definirEstado(g, {
        verificando: false,
        conflitos: [],
        falha: 'RN1: o término deve ser posterior ao início. Ajuste o horário de término.',
      });
      return;
    }
    const periodo = { inicio, termino };
    this.definirEstado(g, { verificando: true, conflitos: [] });
    const id = this.reserva()?.id;
    const requisicao$ = this.demonstracao()
      ? of({ conflitos: verificarDemo(amb, periodo, id) })
      : this.servico.verificarPeriodo({ ambienteId: amb, periodo, reservaId: id ?? undefined }).pipe(timeout(2000));
    requisicao$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (r) => {
        // Ignora respostas de valores que já mudaram.
        const atual = g.getRawValue();
        if (atual.inicio !== inicio || atual.termino !== termino || this.form.controls.ambienteId.value !== amb) return;
        this.definirEstado(g, { verificando: false, conflitos: r.conflitos ?? [] });
      },
      error: (e: unknown) => {
        if (e instanceof HttpErrorResponse && e.status === 422) {
          const erros = (e.error?.erros ?? []) as ErroApi[];
          this.definirEstado(g, { verificando: false, conflitos: erros.map((x) => ({ codigo: x.codigo, mensagem: x.mensagem })) });
          return;
        }
        if (e instanceof HttpErrorResponse && (e.status === 0 || e.status >= 500)) {
          // API indisponível: segue em modo demonstração.
          this.demonstracao.set(true);
          this.definirEstado(g, { verificando: false, conflitos: verificarDemo(amb, periodo, id) });
          return;
        }
        this.definirEstado(g, {
          verificando: false,
          conflitos: [],
          falha: 'Não foi possível verificar conflitos agora. A verificação será refeita ao salvar.',
        });
      },
    });
  }

  // ---------- Recursos ----------

  protected get recursos(): FormArray<GrupoRecurso> {
    return this.form.controls.recursos;
  }

  private novoRecurso(recursoId: string, quantidade: number | null): GrupoRecurso {
    const limitado = this.recursosCatalogo().find((r) => r.id === recursoId)?.limitado ?? false;
    return this.fb.group({
      recursoId: this.fb.control(recursoId),
      quantidade: new FormControl<number | null>(quantidade, limitado ? [Validators.required, Validators.min(1)] : []),
    });
  }

  private permitido(r: RecursoCatalogo, ambienteId: string): boolean {
    if (!r.ambientesPermitidos?.length) return true;
    return ambienteId !== LOCAL_PROPRIO && r.ambientesPermitidos.includes(ambienteId);
  }

  private filtrarRecursos(ambienteId: string): void {
    if (this.ambienteSemFiltro) return;
    const removidos: string[] = [];
    for (let i = this.recursos.length - 1; i >= 0; i--) {
      const id = this.recursos.at(i).controls.recursoId.value;
      const r = this.recursosCatalogo().find((x) => x.id === id);
      if (r && !this.permitido(r, ambienteId)) {
        removidos.unshift(r.descricao);
        this.recursos.removeAt(i);
      }
    }
    this.aviso.set(
      removidos.length
        ? `RN9: ${removidos.join(', ')} não ${removidos.length > 1 ? 'são permitidos' : 'é permitido'} no ambiente escolhido e ${removidos.length > 1 ? 'foram removidos' : 'foi removido'} da reserva.`
        : '',
    );
  }

  protected indiceRecurso(id: string): number {
    return this.recursos.controls.findIndex((g) => g.controls.recursoId.value === id);
  }

  protected alternarRecurso(r: RecursoCatalogo, marcado: boolean): void {
    const i = this.indiceRecurso(r.id);
    if (marcado && i < 0) this.recursos.push(this.novoRecurso(r.id, r.limitado ? 1 : null));
    if (!marcado && i >= 0) this.recursos.removeAt(i);
  }

  // ---------- Erros ----------

  private campoConhecido(campo: string): boolean {
    if (/^periodos\[\d+\]/.test(campo)) return true;
    if (/^recursos\[\d+\]/.test(campo)) return true;
    return ['ambienteId', 'finalidade', 'participantes', 'disposicaoId', 'complemento', 'periodos'].includes(campo);
  }

  /** Mensagens 422 de um campo (aceita também subcampos como `periodos[0].inicio`). */
  protected errosDe(campo: string): ErroApi[] {
    return this.errosApi().filter(
      (e) => e.campo === campo || e.campo?.startsWith(campo + '.') || e.campo?.startsWith(campo + '['),
    );
  }

  /** Erros 422 de uma solicitação de Recurso, pelo id do recurso. */
  protected errosRecurso(id: string): ErroApi[] {
    const i = this.indiceRecurso(id);
    return i < 0 ? [] : this.errosDe(`recursos[${i}]`);
  }

  // ---------- Salvamento ----------

  private montar(): Reserva {
    const v = this.form.getRawValue();
    const catalogo = this.recursosCatalogo();
    return {
      ...(this.reserva() ?? {}),
      ambienteId: v.ambienteId === LOCAL_PROPRIO ? null : v.ambienteId,
      finalidade: v.finalidade,
      participantes: v.participantes,
      disposicaoId: v.ambienteId === LOCAL_PROPRIO ? null : v.disposicaoId,
      complemento: v.complemento,
      periodos: v.periodos.map((p) => ({ inicio: p.inicio, termino: p.termino })),
      recursos: v.recursos.map((s) =>
        catalogo.find((r) => r.id === s.recursoId)?.limitado
          ? { recursoId: s.recursoId, quantidade: s.quantidade ?? undefined }
          : { recursoId: s.recursoId },
      ),
    };
  }

  protected salvar(): void {
    this.operacaoErro.set('salvar');
    this.errosApi.set([]);
    this.aviso.set('');
    this.form.markAllAsTouched();
    const dados = this.montar();
    const nome = this.auth.usuario()?.nome ?? 'Solicitante';
    if (this.demonstracao()) {
      this.concluir(salvarDemo(dados, nome));
      return;
    }
    this.salvando.set(true);
    const op$ = this.reservaId ? this.servico.alterar(this.reservaId, dados) : this.servico.criar(dados);
    op$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (r) => this.concluir(r),
      error: (e: HttpErrorResponse) => {
        this.salvando.set(false);
        const erros = (e.error?.erros ?? []) as ErroApi[];
        if (e.status === 422 || e.status === 409 || e.status === 400 || e.status === 403) {
          this.errosApi.set(
            erros.length
              ? erros
              : [{ codigo: String(e.status), mensagem: 'Não foi possível salvar a reserva. Revise os dados e tente novamente.' }],
          );
          this.focarResumo();
          return;
        }
        // API indisponível: grava em memória no modo demonstração.
        this.demonstracao.set(true);
        this.concluir(salvarDemo(dados, nome));
      },
    });
  }

  private concluir(r: Reserva): void {
    this.salvando.set(false);
    const mensagem = this.reservaId ? 'Reserva alterada com sucesso.' : 'Reserva cadastrada com sucesso.';
    if (this.reservaId) {
      this.preencher(r);
      this.aviso.set(mensagem);
    } else {
      void this.router.navigate(['/reservas', r.id], { state: { mensagem } });
    }
  }

  // ---------- Cancelamento (Req. 12.5) ----------

  /** Abre o diálogo de confirmação; o MatDialog devolve o foco ao gatilho ao fechar. */
  protected pedirCancelamento(): void {
    const id = this.reserva()?.id ?? this.reservaId;
    if (!id || !this.podeCancelar()) return;
    this.dialogo
      .open<DialogoCancelamento, DadosDialogoCancelamento, boolean>(DialogoCancelamento, {
        data: { reservaId: id },
        role: 'alertdialog',
        ariaLabelledBy: 'titulo-dialogo-cancelamento',
        ariaDescribedBy: 'descricao-dialogo-cancelamento',
        autoFocus: '#botao-voltar-cancelamento',
        restoreFocus: true,
        disableClose: false,
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmado) => {
        if (confirmado) this.cancelar(id);
      });
  }

  private cancelar(id: string): void {
    this.operacaoErro.set('cancelar');
    this.errosApi.set([]);
    this.aviso.set('');
    if (this.demonstracao()) {
      this.concluirCancelamentoDemo(id);
      return;
    }
    this.cancelando.set(true);
    this.servico
      .cancelar(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          // Recarrega a reserva para refletir status e última alteração calculados no backend.
          this.servico
            .obter(id)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
              next: (r) => this.concluirCancelamento(r),
              error: () => this.concluirCancelamento({ ...this.reserva()!, status: 'CANCELADA' }),
            });
        },
        error: (e: HttpErrorResponse) => {
          this.cancelando.set(false);
          const erros = (e.error?.erros ?? []) as ErroApi[];
          if (e.status === 422 || e.status === 409 || e.status === 400 || e.status === 403) {
            this.errosApi.set(
              erros.length
                ? erros
                : [{ codigo: String(e.status), mensagem: 'Não foi possível cancelar a reserva. Tente novamente mais tarde.' }],
            );
            this.focarResumo();
            return;
          }
          // API indisponível: cancela em memória no modo demonstração.
          this.demonstracao.set(true);
          this.concluirCancelamentoDemo(id);
        },
      });
  }

  private concluirCancelamentoDemo(id: string): void {
    const r = cancelarDemo(id) ?? { ...this.reserva()!, status: 'CANCELADA' as const };
    this.concluirCancelamento(r);
  }

  private concluirCancelamento(r: Reserva): void {
    this.cancelando.set(false);
    this.preencher(r);
    this.aviso.set('Reserva cancelada.');
  }

  private focarResumo(): void {
    queueMicrotask(() => document.getElementById('resumo-erros')?.focus());
  }

  protected nomeSolicitante(): string {
    return this.reserva()?.solicitanteNome ?? this.auth.usuario()?.nome ?? '';
  }
}
