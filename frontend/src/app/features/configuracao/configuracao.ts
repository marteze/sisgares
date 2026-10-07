import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

/** Faixa horária `HH:mm` (Dtos.Faixa do backend). */
interface Faixa {
  minimo: string;
  maximo: string;
}

/** Corpo de `GET|PUT /api/configuracao` (Req. 8). */
interface ConfiguracaoDto {
  antecedenciaMinutos: number;
  faixaGlobal: Faixa;
  faixasPorUnidade: Record<string, Faixa>;
  snpUrl?: string;
}

interface ErroApi {
  codigo: string;
  mensagem: string;
  campo?: string;
}

type GrupoFaixa = FormGroup<{ minimo: FormControl<string>; maximo: FormControl<string> }>;
type GrupoUnidade = FormGroup<{ unidade: FormControl<string>; minimo: FormControl<string>; maximo: FormControl<string> }>;

/** Configuração fictícia para o modo demonstração. */
const CONFIGURACAO_DEMO: ConfiguracaoDto = {
  antecedenciaMinutos: 120,
  faixaGlobal: { minimo: '07:00', maximo: '20:00' },
  faixasPorUnidade: { 'PR-DF': { minimo: '08:00', maximo: '18:00' } },
  snpUrl: 'https://snp.exemplo.gov.br/api',
};

/** Exige mínimo < máximo (comparação textual de `HH:mm`). */
function validarFaixa(g: AbstractControl): ValidationErrors | null {
  const { minimo, maximo } = g.value as Faixa;
  return minimo && maximo && minimo >= maximo ? { faixa: true } : null;
}

/** Unidades das faixas não podem se repetir. */
function validarUnidadesUnicas(a: AbstractControl): ValidationErrors | null {
  const nomes = (a.value as { unidade: string }[]).map((f) => f.unidade.trim().toUpperCase()).filter(Boolean);
  return new Set(nomes).size !== nomes.length ? { duplicada: true } : null;
}

/** Tela de Configuração do Administrador (`/configuracao`, Req. 8.1, 8.2, 8.4). */
@Component({
  selector: 'app-configuracao',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <section class="configuracao" aria-labelledby="titulo-pagina">
      <h2 id="titulo-pagina" tabindex="-1">Configuração</h2>
      <p class="status" [class.aviso-demo]="demonstracao()" role="status" aria-live="polite">{{ mensagem() }}</p>
      <div class="erros" role="alert" aria-live="assertive">
        @if (erros().length) {
          <p><strong>Não foi possível salvar:</strong></p>
          <ul>
            @for (e of erros(); track $index) {
              <li>{{ e.mensagem }}</li>
            }
          </ul>
        }
      </div>

      @if (!carregando()) {
        <form [formGroup]="form" (ngSubmit)="salvar()" novalidate>
          <mat-form-field appearance="outline">
            <mat-label>Antecedência mínima (minutos)</mat-label>
            <input matInput type="number" min="0" max="10080" required formControlName="antecedenciaMinutos" />
            <mat-hint>De 0 a 10080 minutos (7 dias).</mat-hint>
            <mat-error>{{ erroAntecedencia() }}</mat-error>
          </mat-form-field>

          <fieldset [formGroup]="form.controls.faixaGlobal">
            <legend>Faixa horária global</legend>
            <div class="linha">
              <mat-form-field appearance="outline">
                <mat-label>Início</mat-label>
                <input matInput type="time" required formControlName="minimo" />
                <mat-error>{{ erroControle(form.controls.faixaGlobal.controls.minimo) }}</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Término</mat-label>
                <input matInput type="time" required formControlName="maximo" />
                <mat-error>{{ erroControle(form.controls.faixaGlobal.controls.maximo) }}</mat-error>
              </mat-form-field>
            </div>
            @if (faixaInvalida(form.controls.faixaGlobal)) {
              <p class="erro-campo" id="erro-faixa-global">O início deve ser anterior ao término.</p>
            }
          </fieldset>

          <fieldset>
            <legend>Faixas por unidade</legend>
            @if (!unidades.length) {
              <p>Nenhuma faixa específica; vale a faixa global.</p>
            }
            @for (g of unidades.controls; track g; let i = $index) {
              <div class="linha" [formGroup]="g" role="group" [attr.aria-label]="'Faixa da unidade ' + (i + 1)">
                <mat-form-field appearance="outline">
                  <mat-label>Unidade</mat-label>
                  <input matInput required formControlName="unidade" />
                  <mat-error>{{ erroControle(g.controls.unidade) }}</mat-error>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Início</mat-label>
                  <input matInput type="time" required formControlName="minimo" />
                  <mat-error>{{ erroControle(g.controls.minimo) }}</mat-error>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Término</mat-label>
                  <input matInput type="time" required formControlName="maximo" />
                  <mat-error>{{ erroControle(g.controls.maximo) }}</mat-error>
                </mat-form-field>
                <button mat-stroked-button type="button" [attr.aria-label]="'Remover faixa da unidade ' + (g.value.unidade || i + 1)" (click)="removerUnidade(i)">
                  Remover
                </button>
                @if (faixaInvalida(g)) {
                  <p class="erro-campo">O início deve ser anterior ao término.</p>
                }
              </div>
            }
            @if (unidades.hasError('duplicada')) {
              <p class="erro-campo">Há unidades repetidas. Informe cada unidade uma única vez.</p>
            }
            <button mat-stroked-button type="button" (click)="adicionarUnidade()">Adicionar faixa por unidade</button>
          </fieldset>

          <mat-form-field appearance="outline" class="largo">
            <mat-label>URL do SNP</mat-label>
            <input matInput type="url" formControlName="snpUrl" />
            <mat-hint>Endereço HTTPS da API do SNP.</mat-hint>
            <mat-error>{{ erroControle(form.controls.snpUrl) }}</mat-error>
          </mat-form-field>

          <div>
            <button mat-flat-button type="submit" [disabled]="salvando()">{{ salvando() ? 'Salvando…' : 'Salvar configuração' }}</button>
          </div>
        </form>
      }
    </section>
  `,
  styles: `
    .configuracao { padding: 1rem; min-width: 0; max-width: 56rem; }
    form { display: flex; flex-direction: column; gap: 1rem; }
    fieldset { border: 1px solid #8c8c8c; padding: .75rem 1rem; display: flex; flex-direction: column; gap: .5rem; }
    legend { font-weight: 600; padding: 0 .25rem; }
    .linha { display: flex; flex-wrap: wrap; gap: .75rem; align-items: flex-start; }
    .largo { width: 100%; }
    .status { min-height: 1.5em; }
    .aviso-demo { padding: .5rem .75rem; border-left: 4px solid #8a5a00; background: #fff4dc; color: #4a3000; }
    .erros:not(:empty) { padding: .5rem .75rem; border-left: 4px solid #a1001a; background: #fdecee; color: #5c0010; }
    .erro-campo { color: #a1001a; font-weight: 600; margin: 0; width: 100%; }
    h2:focus-visible { outline: 3px solid #1a4fa0; outline-offset: 2px; }
  `,
})
export class Configuracao {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly demonstracao = signal(false);
  protected readonly aviso = signal('');
  protected readonly erros = signal<ErroApi[]>([]);
  /** Cópia em memória usada no modo demonstração. */
  private demo: ConfiguracaoDto = structuredClone(CONFIGURACAO_DEMO);

  protected readonly form = this.fb.group({
    antecedenciaMinutos: this.fb.control<number | null>(null, [
      Validators.required,
      Validators.min(0),
      Validators.max(10080),
    ]),
    faixaGlobal: this.novaFaixa('', ''),
    faixasPorUnidade: this.fb.array<GrupoUnidade>([], validarUnidadesUnicas),
    snpUrl: this.fb.control('', Validators.pattern(/^https?:\/\/\S+$/)),
  });

  protected get unidades(): FormArray<GrupoUnidade> {
    return this.form.controls.faixasPorUnidade;
  }

  protected readonly mensagem = computed(() => {
    if (this.carregando()) return 'Carregando configuração…';
    const base = this.aviso();
    return this.demonstracao()
      ? `${base} Atenção: não foi possível acessar o servidor; os dados são de demonstração e as alterações ficam apenas na memória do navegador.`.trim()
      : base;
  });

  constructor() {
    this.http
      .get<ConfiguracaoDto>('/api/configuracao')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (c) => this.preencher(c),
        error: () => {
          this.demonstracao.set(true);
          this.preencher(this.demo);
        },
      });
  }

  private novaFaixa(minimo: string, maximo: string): GrupoFaixa {
    return this.fb.group(
      { minimo: [minimo, Validators.required], maximo: [maximo, Validators.required] },
      { validators: validarFaixa },
    );
  }

  private novaUnidade(unidade: string, f: Faixa): GrupoUnidade {
    return this.fb.group(
      {
        unidade: [unidade, Validators.required],
        minimo: [f.minimo, Validators.required],
        maximo: [f.maximo, Validators.required],
      },
      { validators: validarFaixa },
    );
  }

  private preencher(c: ConfiguracaoDto): void {
    this.unidades.clear();
    Object.entries(c.faixasPorUnidade ?? {}).forEach(([u, f]) => this.unidades.push(this.novaUnidade(u, f)));
    this.form.patchValue({
      antecedenciaMinutos: c.antecedenciaMinutos,
      faixaGlobal: c.faixaGlobal,
      snpUrl: c.snpUrl ?? '',
    });
    this.carregando.set(false);
  }

  protected adicionarUnidade(): void {
    this.unidades.push(this.novaUnidade('', { minimo: '', maximo: '' }));
  }

  protected removerUnidade(i: number): void {
    this.unidades.removeAt(i);
    this.aviso.set('Faixa removida. Salve para aplicar.');
  }

  protected faixaInvalida(g: AbstractControl): boolean {
    return g.hasError('faixa') && (g.touched || g.dirty);
  }

  protected erroAntecedencia(): string {
    const c = this.form.controls.antecedenciaMinutos;
    if (c.hasError('api')) return c.getError('api') as string;
    if (c.hasError('required')) return 'Informe a antecedência em minutos.';
    if (c.hasError('min') || c.hasError('max')) return 'Informe um valor entre 0 e 10080.';
    return '';
  }

  protected erroControle(c: AbstractControl): string {
    if (c.hasError('api')) return c.getError('api') as string;
    if (c.hasError('required')) return 'Campo obrigatório.';
    if (c.hasError('pattern')) return 'Informe uma URL válida, ex.: https://snp.exemplo.gov.br.';
    return '';
  }

  /** Mensagens de validação no cliente para o resumo `aria-live`. */
  private errosCliente(): ErroApi[] {
    const lista: ErroApi[] = [];
    const add = (mensagem: string, campo?: string) => lista.push({ codigo: 'VALIDACAO', mensagem, campo });
    if (this.form.controls.antecedenciaMinutos.invalid) add(`Antecedência: ${this.erroAntecedencia()}`);
    const fg = this.form.controls.faixaGlobal;
    if (fg.controls.minimo.invalid || fg.controls.maximo.invalid) add('Faixa global: informe início e término.');
    if (fg.hasError('faixa')) add('Faixa global: o início deve ser anterior ao término.');
    this.unidades.controls.forEach((g, i) => {
      const nome = g.value.unidade || `linha ${i + 1}`;
      if (g.controls.unidade.invalid) add(`Faixa ${i + 1}: informe a unidade.`);
      if (g.controls.minimo.invalid || g.controls.maximo.invalid) add(`Faixa de ${nome}: informe início e término.`);
      if (g.hasError('faixa')) add(`Faixa de ${nome}: o início deve ser anterior ao término.`);
    });
    if (this.unidades.hasError('duplicada')) add('Há unidades repetidas nas faixas por unidade.');
    if (this.form.controls.snpUrl.invalid) add('URL do SNP inválida.');
    return lista;
  }

  protected salvar(): void {
    this.aviso.set('');
    this.form.markAllAsTouched();
    const errosCliente = this.errosCliente();
    this.erros.set(errosCliente);
    if (errosCliente.length) return;

    const v = this.form.getRawValue();
    const corpo: ConfiguracaoDto = {
      antecedenciaMinutos: Number(v.antecedenciaMinutos),
      faixaGlobal: v.faixaGlobal,
      faixasPorUnidade: Object.fromEntries(
        v.faixasPorUnidade.map((f) => [f.unidade.trim(), { minimo: f.minimo, maximo: f.maximo }]),
      ),
      snpUrl: v.snpUrl.trim() || undefined,
    };

    if (this.demonstracao()) {
      this.demo = structuredClone(corpo);
      this.aviso.set('Configuração salva.');
      return;
    }
    this.salvando.set(true);
    this.http
      .put<ConfiguracaoDto>('/api/configuracao', corpo)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (c) => {
          this.salvando.set(false);
          this.preencher(c);
          this.aviso.set('Configuração salva.');
        },
        error: (e: unknown) => {
          this.salvando.set(false);
          this.aplicarErros(e);
        },
      });
  }

  /** Associa erros 422 ao campo (caminho com pontos, ex.: `faixaGlobal.minimo`) e ao resumo. */
  private aplicarErros(e: unknown): void {
    if (!(e instanceof HttpErrorResponse) || e.status !== 422) {
      this.erros.set([{ codigo: 'ERRO', mensagem: 'Falha ao comunicar com o servidor. Tente novamente em instantes.' }]);
      return;
    }
    const erros = ((e.error?.erros ?? []) as ErroApi[]).filter((x) => x?.mensagem);
    for (const erro of erros) {
      const controle = erro.campo ? this.localizar(erro.campo) : null;
      if (controle) {
        controle.setErrors({ ...(controle.errors ?? {}), api: erro.mensagem });
        controle.markAsTouched();
      }
    }
    this.erros.set(erros.length ? erros : [{ codigo: 'ERRO', mensagem: 'Dados inválidos. Revise o formulário.' }]);
  }

  private localizar(campo: string): AbstractControl | null {
    const direto = this.form.get(campo);
    if (direto) return direto;
    // `faixasPorUnidade.{UNIDADE}[.minimo|.maximo]` → linha correspondente.
    const [raiz, unidade, sub] = campo.split('.');
    if (raiz !== 'faixasPorUnidade' || !unidade) return null;
    const g = this.unidades.controls.find((x) => x.value.unidade?.trim() === unidade);
    return g ? (sub ? g.get(sub) : g.controls.unidade) : null;
  }
}
