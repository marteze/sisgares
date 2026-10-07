import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormRecord, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute } from '@angular/router';
import { Observable, forkJoin, of, switchMap } from 'rxjs';
import {
  CONFIGS_CADASTRO,
  CampoCadastro,
  ConfigCadastro,
  EntidadeCadastro,
  ItemCadastro,
  separarEmails,
} from './cadastros.config';
import { CadastrosService } from './cadastros.service';
import { DadosDialogoConfirmacao, DialogoConfirmacao } from './dialogo-confirmacao';

interface ErroApi {
  codigo: string;
  mensagem: string;
  campo?: string;
}

/** Tipos aceitos para a imagem da Disposição (validados também no backend). */
const TIPOS_IMAGEM = ['image/png', 'image/jpeg', 'image/svg+xml'];
const LIMITE_IMAGEM = 2 * 1024 * 1024;
const PADRAO_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Valida uma lista de e-mails separados por linha. */
function validarListaEmails(c: AbstractControl): ValidationErrors | null {
  const invalidos = separarEmails(c.value).filter((e) => !PADRAO_EMAIL.test(e));
  return invalidos.length ? { listaEmails: invalidos.join(', ') } : null;
}

/**
 * Tela genérica de lista + formulário das entidades do catálogo (`/cadastros/*`, Req. 7).
 * A entidade vem de `data.entidade` da rota; campos e colunas vêm de `CONFIGS_CADASTRO`.
 */
@Component({
  selector: 'app-cadastro-generico',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <section class="cadastro" aria-labelledby="titulo-pagina">
      <div class="topo">
        <h2 id="titulo-pagina" tabindex="-1">{{ config.titulo }}</h2>
        @if (!formularioAberto()) {
          <button mat-flat-button type="button" (click)="abrirFormulario(null)">Incluir</button>
        }
      </div>
      <p class="status" [class.aviso-demo]="demonstracao()" role="status" aria-live="polite">{{ mensagem() }}</p>
      <div class="erros" role="alert" aria-live="assertive">
        @if (errosApi().length) {
          <p><strong>Não foi possível salvar:</strong></p>
          <ul>
            @for (e of errosApi(); track $index) {
              <li>{{ e.mensagem }}</li>
            }
          </ul>
        }
      </div>

      @if (formularioAberto()) {
        <form class="formulario" [formGroup]="form" (ngSubmit)="salvar()" novalidate aria-labelledby="titulo-formulario">
          <h3 id="titulo-formulario" tabindex="-1" #tituloFormulario>
            {{ emEdicao() ? 'Editar ' + config.rotulo(emEdicao()!) : 'Incluir em ' + config.titulo }}
          </h3>
          @for (campo of camposVisiveis(); track campo.nome) {
            @switch (campo.tipo) {
              @case ('booleano') {
                <mat-checkbox [formControlName]="campo.nome">{{ campo.rotulo }}</mat-checkbox>
              }
              @case ('imagem') {
                <div class="campo-imagem">
                  <label for="campo-imagem">{{ campo.rotulo }}</label>
                  <input
                    id="campo-imagem"
                    type="file"
                    accept="image/png,image/jpeg,image/svg+xml"
                    [attr.aria-invalid]="erroImagem() ? true : null"
                    [attr.aria-describedby]="erroImagem() ? 'erro-imagem' : 'dica-imagem'"
                    (change)="selecionarImagem($event)"
                  />
                  <span id="dica-imagem" class="dica">
                    {{ imagem() ? 'Selecionada: ' + imagem()!.nome : emEdicao()?.['imagem'] ? 'Já existe imagem; selecione outra para substituir.' : 'Opcional.' }}
                  </span>
                  @if (erroImagem()) {
                    <span id="erro-imagem" class="erro-campo">{{ erroImagem() }}</span>
                  }
                </div>
              }
              @case ('selecao') {
                <mat-form-field appearance="outline">
                  <mat-label>{{ campo.rotulo }}</mat-label>
                  <mat-select [formControlName]="campo.nome" [required]="!!campo.obrigatorio">
                    @if (!campo.obrigatorio) {
                      <mat-option value="">{{ campo.opcaoVazia ?? 'Nenhum' }}</mat-option>
                    }
                    @for (o of opcoesDe(campo); track o.id) {
                      <mat-option [value]="o.id">{{ o.rotulo }}</mat-option>
                    }
                  </mat-select>
                  @if (campo.dica) {
                    <mat-hint>{{ campo.dica }}</mat-hint>
                  }
                  <mat-error>{{ mensagemErro(campo) }}</mat-error>
                </mat-form-field>
              }
              @case ('lista-emails') {
                <mat-form-field appearance="outline">
                  <mat-label>{{ campo.rotulo }}</mat-label>
                  <textarea matInput rows="3" [formControlName]="campo.nome"></textarea>
                  @if (campo.dica) {
                    <mat-hint>{{ campo.dica }}</mat-hint>
                  }
                  <mat-error>{{ mensagemErro(campo) }}</mat-error>
                </mat-form-field>
              }
              @default {
                <mat-form-field appearance="outline">
                  <mat-label>{{ campo.rotulo }}</mat-label>
                  <input
                    matInput
                    [type]="campo.tipo === 'numero' ? 'number' : campo.tipo === 'email' ? 'email' : 'text'"
                    [attr.min]="campo.min ?? null"
                    [attr.max]="campo.max ?? null"
                    [required]="!!campo.obrigatorio"
                    [formControlName]="campo.nome"
                  />
                  @if (campo.dica) {
                    <mat-hint>{{ campo.dica }}</mat-hint>
                  }
                  <mat-error>{{ mensagemErro(campo) }}</mat-error>
                </mat-form-field>
              }
            }
          }
          <div class="acoes">
            <button mat-flat-button type="submit" [disabled]="salvando()">{{ salvando() ? 'Salvando…' : 'Salvar' }}</button>
            <button mat-stroked-button type="button" (click)="fecharFormulario()">Cancelar</button>
          </div>
        </form>
      }

      <mat-checkbox [checked]="incluirInativos()" (change)="alternarInativos($event.checked)">Incluir inativos</mat-checkbox>

      @if (itens().length) {
        <div class="tabela-rolagem" tabindex="0" role="region" [attr.aria-label]="'Lista de ' + config.titulo">
          <table>
            <caption>{{ config.titulo }} cadastrados</caption>
            <thead>
              <tr>
                @for (c of config.colunas; track c.titulo) {
                  <th scope="col">{{ c.titulo }}</th>
                }
                <th scope="col">Situação</th>
                <th scope="col">Ações</th>
              </tr>
            </thead>
            <tbody>
              @for (item of itens(); track item.id) {
                <tr [class.inativo]="!item.ativo">
                  @for (c of config.colunas; track c.titulo) {
                    <td>{{ c.valor(item, rotuloDe) }}</td>
                  }
                  <td>{{ item.ativo ? 'Ativo' : 'Inativo' }}</td>
                  <td class="acoes-linha">
                    <button mat-stroked-button type="button" [attr.aria-label]="'Editar ' + config.rotulo(item)" (click)="abrirFormulario(item)">
                      Editar
                    </button>
                    @if (item.ativo) {
                      <button mat-stroked-button type="button" [attr.aria-label]="'Inativar ' + config.rotulo(item)" (click)="inativar(item)">
                        Inativar
                      </button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  styles: `
    .cadastro { padding: 1rem; min-width: 0; display: flex; flex-direction: column; gap: .75rem; }
    .topo { display: flex; flex-wrap: wrap; gap: 1rem; align-items: center; justify-content: space-between; }
    .status { min-height: 1.5em; margin: 0; }
    .aviso-demo { padding: .5rem .75rem; border-left: 4px solid #8a5a00; background: #fff4dc; color: #4a3000; }
    .erros:not(:empty) { padding: .5rem .75rem; border-left: 4px solid #a1001a; background: #fdecee; color: #5c0010; }
    .formulario { display: flex; flex-direction: column; gap: .5rem; max-width: 40rem; padding: 1rem; border: 1px solid #8c8c8c; }
    .campo-imagem { display: flex; flex-direction: column; gap: .25rem; }
    .dica { font-size: .875rem; color: #404040; }
    .erro-campo { color: #a1001a; font-weight: 600; }
    .acoes, .acoes-linha { display: flex; flex-wrap: wrap; gap: .5rem; }
    .tabela-rolagem { max-width: 100%; overflow-x: auto; }
    .tabela-rolagem:focus-visible, input[type='file']:focus-visible, h2:focus-visible, h3:focus-visible {
      outline: 3px solid #1a4fa0; outline-offset: 2px;
    }
    table { border-collapse: collapse; width: 100%; }
    caption { text-align: left; font-weight: 600; padding: .5rem 0; }
    th, td { border: 1px solid #8c8c8c; padding: .25rem .5rem; text-align: left; }
    thead th { background: #e8eef7; color: #102040; }
    tr.inativo td { color: #404040; font-style: italic; }
  `,
})
export class CadastroGenerico {
  private readonly servico = inject(CadastrosService);
  private readonly dialogo = inject(MatDialog);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);
  private readonly tituloFormulario = viewChild<ElementRef<HTMLElement>>('tituloFormulario');

  protected readonly config: ConfigCadastro =
    CONFIGS_CADASTRO[inject(ActivatedRoute).snapshot.data['entidade'] as EntidadeCadastro];

  protected readonly itens = signal<ItemCadastro[]>([]);
  /** Itens ativos das entidades usadas em campos de seleção (pai, grupo). */
  protected readonly opcoes = signal<Partial<Record<EntidadeCadastro, ItemCadastro[]>>>({});
  protected readonly incluirInativos = signal(false);
  protected readonly carregando = signal(true);
  protected readonly demonstracao = signal(false);
  protected readonly salvando = signal(false);
  protected readonly aviso = signal('');
  protected readonly errosApi = signal<ErroApi[]>([]);
  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<ItemCadastro | null>(null);
  protected readonly imagem = signal<{ nome: string; conteudo: string } | null>(null);
  protected readonly erroImagem = signal('');
  /** Valores atuais do formulário (para campos condicionais). */
  private readonly valores = signal<Record<string, unknown>>({});

  protected form = new FormRecord<FormControl<unknown>>({});

  protected readonly camposVisiveis = computed(() =>
    this.config.campos.filter((c) => !c.visivel || c.visivel(this.valores())),
  );

  protected readonly mensagem = computed(() => {
    if (this.carregando()) return `Carregando ${this.config.titulo.toLowerCase()}…`;
    const base = this.aviso() || `${this.itens().length} registro(s) encontrado(s).`;
    return this.demonstracao()
      ? `${base} Atenção: não foi possível acessar o servidor; os dados exibidos são de demonstração e ficam apenas na memória do navegador.`
      : base;
  });

  /** Rótulo de um item de outra entidade (colunas de pai/grupo). */
  protected readonly rotuloDe = (entidade: EntidadeCadastro, id: unknown): string => {
    const lista = [...(this.opcoes()[entidade] ?? []), ...(entidade === this.config.entidade ? this.itens() : [])];
    const item = lista.find((i) => i.id === String(id));
    return item ? CONFIGS_CADASTRO[entidade].rotulo(item) : String(id ?? '');
  };

  constructor() {
    this.carregar();
  }

  /** Carrega a lista e as opções de seleção; em falha da API, entra no modo demonstração. */
  private carregar(): void {
    const entidade = this.config.entidade;
    const todos = this.incluirInativos();
    const relacionadas = [...new Set(this.config.campos.flatMap((c) => (c.opcoes ? [c.opcoes] : [])))];
    this.carregando.set(true);
    if (this.demonstracao()) {
      this.aplicarDados(this.servico.listarDemo(entidade, todos), relacionadas, (e) => this.servico.listarDemo(e, false));
      return;
    }
    forkJoin({
      itens: this.servico.listar(entidade, todos),
      rel: relacionadas.length ? forkJoin(relacionadas.map((e) => this.servico.listar(e, false))) : of([]),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ itens, rel }) => this.aplicarDados(itens, relacionadas, (_e, i) => rel[i]),
        error: () => {
          this.demonstracao.set(true);
          this.carregar();
        },
      });
  }

  private aplicarDados(
    itens: ItemCadastro[],
    relacionadas: EntidadeCadastro[],
    obter: (e: EntidadeCadastro, indice: number) => ItemCadastro[],
  ): void {
    this.itens.set(itens);
    this.opcoes.set(Object.fromEntries(relacionadas.map((e, i) => [e, obter(e, i)])));
    this.carregando.set(false);
  }

  protected alternarInativos(valor: boolean): void {
    this.incluirInativos.set(valor);
    this.aviso.set('');
    this.carregar();
  }

  protected opcoesDe(campo: CampoCadastro): { id: string; rotulo: string }[] {
    if (!campo.opcoes) return [];
    const cfg = CONFIGS_CADASTRO[campo.opcoes];
    const atual = this.emEdicao()?.id;
    return (this.opcoes()[campo.opcoes] ?? [])
      // Um Ambiente não pode ser pai de si mesmo.
      .filter((o) => !(campo.opcoes === this.config.entidade && o.id === atual))
      .map((o) => ({ id: o.id, rotulo: cfg.rotulo(o) }));
  }

  protected abrirFormulario(item: ItemCadastro | null): void {
    const valores = item ? this.config.paraFormulario(item) : {};
    const form = new FormRecord<FormControl<unknown>>({});
    for (const campo of this.config.campos) {
      if (campo.tipo === 'imagem') continue;
      const validadores = [];
      if (campo.obrigatorio && campo.tipo !== 'booleano') validadores.push(Validators.required);
      if (campo.tipo === 'email') validadores.push(Validators.pattern(PADRAO_EMAIL));
      if (campo.tipo === 'lista-emails') validadores.push(validarListaEmails);
      if (campo.min !== undefined) validadores.push(Validators.min(campo.min));
      if (campo.max !== undefined) validadores.push(Validators.max(campo.max));
      const inicial = valores[campo.nome] ?? (campo.tipo === 'booleano' ? false : campo.tipo === 'numero' ? null : '');
      form.addControl(campo.nome, new FormControl<unknown>(inicial, validadores));
    }
    this.form = form;
    this.valores.set(form.getRawValue());
    form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.valores.set(form.getRawValue()));
    this.emEdicao.set(item);
    this.imagem.set(null);
    this.erroImagem.set('');
    this.errosApi.set([]);
    this.formularioAberto.set(true);
    afterNextRender(() => this.tituloFormulario()?.nativeElement.focus(), { injector: this.injector });
  }

  protected fecharFormulario(): void {
    this.formularioAberto.set(false);
    this.emEdicao.set(null);
    this.errosApi.set([]);
  }

  protected mensagemErro(campo: CampoCadastro): string {
    const erros = this.form.get(campo.nome)?.errors;
    if (!erros) return '';
    if (erros['api']) return erros['api'] as string;
    if (erros['required']) return `Informe ${campo.rotulo.toLowerCase()}.`;
    if (erros['pattern']) return 'Informe um e-mail válido, ex.: nome@exemplo.gov.br.';
    if (erros['listaEmails']) return `E-mail(s) inválido(s): ${erros['listaEmails']}.`;
    if (erros['min']) return `O valor mínimo é ${campo.min}.`;
    if (erros['max']) return `O valor máximo é ${campo.max}.`;
    return 'Valor inválido.';
  }

  /** Valida tipo e tamanho no cliente e converte a imagem em data URL base64. */
  protected selecionarImagem(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const arquivo = entrada.files?.[0];
    this.imagem.set(null);
    this.erroImagem.set('');
    if (!arquivo) return;
    if (!TIPOS_IMAGEM.includes(arquivo.type)) {
      this.erroImagem.set('Formato não aceito. Envie uma imagem PNG, JPEG ou SVG.');
      entrada.value = '';
      return;
    }
    if (arquivo.size > LIMITE_IMAGEM) {
      this.erroImagem.set('A imagem excede 2 MB. Reduza o arquivo e tente novamente.');
      entrada.value = '';
      return;
    }
    const leitor = new FileReader();
    leitor.onload = () => this.imagem.set({ nome: arquivo.name, conteudo: String(leitor.result) });
    leitor.onerror = () => this.erroImagem.set('Não foi possível ler o arquivo. Tente novamente.');
    leitor.readAsDataURL(arquivo);
  }

  protected salvar(): void {
    this.errosApi.set([]);
    // Campos ocultos (ex.: disponibilidade de recurso ilimitado) não bloqueiam o envio.
    const visiveis = new Set(this.camposVisiveis().map((c) => c.nome));
    const invalidos = this.config.campos.filter((c) => visiveis.has(c.nome) && this.form.get(c.nome)?.invalid);
    if (invalidos.length || this.erroImagem()) {
      this.form.markAllAsTouched();
      this.errosApi.set([
        ...invalidos.map((c) => ({ codigo: 'VALIDACAO', mensagem: `${c.rotulo}: ${this.mensagemErro(c)}`, campo: c.nome })),
        ...(this.erroImagem() ? [{ codigo: 'VALIDACAO', mensagem: this.erroImagem(), campo: 'imagem' }] : []),
      ]);
      return;
    }
    const entidade = this.config.entidade;
    const id = this.emEdicao()?.id ?? null;
    const corpo = this.config.paraRequisicao(this.form.getRawValue());
    const imagem = this.imagem();
    this.salvando.set(true);

    let operacao: Observable<unknown>;
    if (this.demonstracao()) {
      operacao = this.servico.salvarDemo(entidade, id, corpo).pipe(
        switchMap((salvo) => {
          if (imagem) this.servico.imagemDemo(salvo.id, imagem.conteudo);
          return of(salvo);
        }),
      );
    } else {
      const base = id ? this.servico.alterar(entidade, id, corpo) : this.servico.criar(entidade, corpo);
      operacao = base.pipe(
        switchMap((salvo) => (imagem && entidade === 'disposicoes' ? this.servico.enviarImagem(salvo.id, imagem.conteudo) : of(salvo))),
      );
    }

    operacao.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.salvando.set(false);
        this.fecharFormulario();
        this.aviso.set(`Registro ${id ? 'alterado' : 'incluído'} com sucesso.`);
        this.carregar();
      },
      error: (e: unknown) => {
        this.salvando.set(false);
        this.aplicarErros(e);
      },
    });
  }

  /** Exibe erros 422 junto ao campo correspondente e no resumo `aria-live`. */
  private aplicarErros(e: unknown): void {
    if (e instanceof HttpErrorResponse && (e.status === 422 || e.status === 409 || e.status === 400)) {
      const erros = ((e.error?.erros ?? []) as ErroApi[]).filter((x) => x?.mensagem);
      for (const erro of erros) {
        const controle = erro.campo ? this.form.get(erro.campo) : null;
        if (controle) {
          controle.setErrors({ ...(controle.errors ?? {}), api: erro.mensagem });
          controle.markAsTouched();
        } else if (erro.campo === 'imagem' || erro.campo === 'conteudo') {
          this.erroImagem.set(erro.mensagem);
        }
      }
      this.errosApi.set(erros.length ? erros : [{ codigo: 'ERRO', mensagem: 'Dados inválidos. Revise o formulário.' }]);
      return;
    }
    this.errosApi.set([{ codigo: 'ERRO', mensagem: 'Falha ao comunicar com o servidor. Tente novamente em instantes.' }]);
  }

  protected inativar(item: ItemCadastro): void {
    const rotulo = this.config.rotulo(item);
    const dados: DadosDialogoConfirmacao = {
      titulo: `Inativar ${rotulo}?`,
      descricao: `${this.config.singular[0].toUpperCase()}${this.config.singular.slice(1)} deixará de ser oferecido(a) em novas reservas. Reservas existentes não são alteradas.`,
      confirmar: 'Confirmar inativação',
    };
    this.dialogo
      .open(DialogoConfirmacao, {
        data: dados,
        role: 'alertdialog',
        ariaLabelledBy: 'titulo-dialogo-confirmacao',
        ariaDescribedBy: 'descricao-dialogo-confirmacao',
        autoFocus: '#botao-voltar-confirmacao',
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmado) => {
        if (!confirmado) return;
        if (this.demonstracao()) {
          this.servico.inativarDemo(this.config.entidade, item.id);
          this.aviso.set(`${rotulo} inativado(a).`);
          this.carregar();
          return;
        }
        this.servico.inativar(this.config.entidade, item.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
          next: () => {
            this.aviso.set(`${rotulo} inativado(a).`);
            this.carregar();
          },
          error: (e: unknown) => this.aplicarErros(e),
        });
      });
  }
}
