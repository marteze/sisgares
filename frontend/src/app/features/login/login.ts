import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatRadioModule } from '@angular/material/radio';
import { AuthService } from '../../core/auth/auth.service';
import { ROTULO_GRUPO, USUARIOS_FICTICIOS, Usuario } from '../../core/auth/perfis';

/**
 * Tela de login acessível.
 * - Modo `mock`: escolha de um usuário fictício por perfil (grupo de rádio com legenda).
 * - Modo `cognito`: botão que redireciona ao Hosted UI (PKCE).
 */
@Component({
  selector: 'app-login',
  imports: [FormsModule, MatButtonModule, MatCardModule, MatRadioModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);

  protected readonly modo = this.auth.modo;
  protected readonly usuarios = USUARIOS_FICTICIOS;
  protected readonly rotulos = ROTULO_GRUPO;
  protected readonly selecionado = signal<Usuario | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly carregando = signal(false);

  protected entrarMock(): void {
    const usuario = this.selecionado();
    if (!usuario) {
      this.erro.set('Selecione um usuário fictício para entrar.');
      return;
    }
    this.erro.set(null);
    this.auth.entrarComUsuarioFicticio(usuario);
  }

  protected async entrarCognito(): Promise<void> {
    this.erro.set(null);
    this.carregando.set(true);
    try {
      await this.auth.entrarComCognito();
    } catch {
      this.carregando.set(false);
      this.erro.set('Não foi possível iniciar o login. Verifique sua conexão e tente novamente.');
    }
  }
}
