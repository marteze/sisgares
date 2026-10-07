import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Router, UrlTree } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ConfiguracaoCognito } from '../../../environments/ambiente';
import { bytesParaBase64Url, decodificarPayload, gerarJwtFicticio, tokenExpirado } from './jwt';
import { GRUPOS, Grupo, Usuario, normalizarGrupos } from './perfis';

const CHAVE_TOKEN = 'sisgares.token';
const CHAVE_VERIFIER = 'sisgares.pkce.verifier';
const CHAVE_STATE = 'sisgares.pkce.state';
const CHAVE_RETORNO = 'sisgares.retorno';

/** Resposta do endpoint /oauth2/token do Cognito. */
interface RespostaToken {
  id_token: string;
  access_token: string;
  expires_in: number;
  token_type: string;
}

/**
 * Serviço de autenticação com dois modos (definidos no environment):
 * - `mock`: escolha de usuário fictício por perfil; JWT não assinado em sessionStorage.
 * - `cognito`: Hosted UI com Authorization Code + PKCE (cliente público, sem segredo).
 * O frontend só esconde opções; a autorização efetiva é verificada no backend.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly modo = environment.modoAutenticacao;

  private readonly tokenAtual = signal<string | null>(this.lerTokenValido());

  /** Usuário autenticado (ou null). */
  readonly usuario = computed<Usuario | null>(() => {
    const token = this.tokenAtual();
    const payload = token ? decodificarPayload(token) : null;
    if (!payload) return null;
    return {
      sub: String(payload['sub'] ?? ''),
      nome: String(payload['name'] ?? payload['email'] ?? 'Usuário'),
      email: String(payload['email'] ?? ''),
      grupos: normalizarGrupos(payload['cognito:groups']),
    };
  });

  readonly autenticado = computed(() => this.usuario() !== null);
  readonly grupos = computed<Grupo[]>(() => this.usuario()?.grupos ?? []);

  /** Token para o cabeçalho Authorization; null se ausente ou expirado. */
  token(): string | null {
    const token = this.tokenAtual();
    if (!token) return null;
    const payload = decodificarPayload(token);
    if (!payload || tokenExpirado(payload)) {
      this.limparSessao();
      return null;
    }
    return token;
  }

  /** Indica se o usuário pertence a algum dos grupos informados. */
  possuiAlgumGrupo(grupos: readonly Grupo[]): boolean {
    const meus = this.grupos();
    return grupos.some((g) => meus.includes(g));
  }

  /** Rota do painel conforme o perfil (Administrador e Setor_Atendente vão ao painel do atendente). */
  rotaPainel(): string {
    if (this.possuiAlgumGrupo([GRUPOS.ADMINISTRADOR, GRUPOS.SETOR_ATENDENTE])) return '/painel/atendente';
    if (this.possuiAlgumGrupo([GRUPOS.SOLICITANTE])) return '/painel/solicitante';
    return '/acesso-negado';
  }

  /** Destino de `/`: painel do perfil ou tela de login. */
  rotaInicial(): UrlTree {
    return this.router.parseUrl(this.autenticado() ? this.rotaPainel() : '/login');
  }

  /** Guarda a URL desejada para retornar após o login. */
  guardarRetorno(url: string): void {
    sessionStorage.setItem(CHAVE_RETORNO, url);
  }

  /** Modo mock: autentica como o usuário fictício escolhido. */
  entrarComUsuarioFicticio(usuario: Usuario): void {
    if (this.modo !== 'mock') throw new Error('Login simulado disponível apenas no modo mock.');
    const agora = Math.floor(Date.now() / 1000);
    const token = gerarJwtFicticio({
      sub: usuario.sub,
      name: usuario.nome,
      email: usuario.email,
      'cognito:groups': usuario.grupos,
      iss: 'sisgares-mock',
      iat: agora,
      exp: agora + 8 * 3600,
    });
    this.gravarToken(token);
    this.navegarAposLogin();
  }

  /** Modo cognito: redireciona ao Hosted UI com desafio PKCE (S256). */
  async entrarComCognito(): Promise<void> {
    const cfg = this.configCognito();
    const verifier = bytesParaBase64Url(crypto.getRandomValues(new Uint8Array(32)));
    const state = bytesParaBase64Url(crypto.getRandomValues(new Uint8Array(16)));
    const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
    const challenge = bytesParaBase64Url(new Uint8Array(hash));
    sessionStorage.setItem(CHAVE_VERIFIER, verifier);
    sessionStorage.setItem(CHAVE_STATE, state);
    const params = new URLSearchParams({
      response_type: 'code',
      client_id: cfg.clientId,
      redirect_uri: cfg.redirectUri,
      scope: cfg.escopos.join(' '),
      state,
      code_challenge: challenge,
      code_challenge_method: 'S256',
    });
    window.location.assign(`${cfg.dominio}/oauth2/authorize?${params.toString()}`);
  }

  /** Modo cognito: troca o código de autorização por tokens (chamado na rota de callback). */
  async concluirLoginCognito(code: string | null, state: string | null): Promise<void> {
    const cfg = this.configCognito();
    const verifier = sessionStorage.getItem(CHAVE_VERIFIER);
    const stateEsperado = sessionStorage.getItem(CHAVE_STATE);
    sessionStorage.removeItem(CHAVE_VERIFIER);
    sessionStorage.removeItem(CHAVE_STATE);
    if (!code || !verifier || !state || state !== stateEsperado) {
      throw new Error('Não foi possível validar o retorno do login. Tente entrar novamente.');
    }
    const corpo = new HttpParams()
      .set('grant_type', 'authorization_code')
      .set('client_id', cfg.clientId)
      .set('code', code)
      .set('redirect_uri', cfg.redirectUri)
      .set('code_verifier', verifier);
    const resposta = await firstValueFrom(
      this.http.post<RespostaToken>(`${cfg.dominio}/oauth2/token`, corpo.toString(), {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      }),
    );
    // O id_token contém `cognito:groups`, nome e e-mail; é enviado como Bearer à API.
    this.gravarToken(resposta.id_token);
    this.navegarAposLogin();
  }

  /** Encerra a sessão local (e no Cognito, quando aplicável). */
  sair(): void {
    this.limparSessao();
    if (this.modo === 'cognito' && environment.cognito) {
      const cfg = environment.cognito;
      const params = new URLSearchParams({ client_id: cfg.clientId, logout_uri: cfg.logoutUri });
      window.location.assign(`${cfg.dominio}/logout?${params.toString()}`);
      return;
    }
    void this.router.navigateByUrl('/login');
  }

  private navegarAposLogin(): void {
    const retorno = sessionStorage.getItem(CHAVE_RETORNO);
    sessionStorage.removeItem(CHAVE_RETORNO);
    // Aceita apenas caminhos internos para evitar redirecionamento aberto.
    const destino = retorno && retorno.startsWith('/') && !retorno.startsWith('//') && retorno !== '/login'
      ? retorno
      : this.rotaPainel();
    void this.router.navigateByUrl(destino);
  }

  private configCognito(): ConfiguracaoCognito {
    if (this.modo !== 'cognito' || !environment.cognito) {
      throw new Error('Configuração do Cognito ausente no environment.');
    }
    return environment.cognito;
  }

  private gravarToken(token: string): void {
    sessionStorage.setItem(CHAVE_TOKEN, token);
    this.tokenAtual.set(token);
  }

  private limparSessao(): void {
    sessionStorage.removeItem(CHAVE_TOKEN);
    this.tokenAtual.set(null);
  }

  private lerTokenValido(): string | null {
    const token = sessionStorage.getItem(CHAVE_TOKEN);
    const payload = token ? decodificarPayload(token) : null;
    if (!token || !payload || tokenExpirado(payload)) {
      sessionStorage.removeItem(CHAVE_TOKEN);
      return null;
    }
    return token;
  }
}
