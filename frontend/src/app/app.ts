import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { AuthService } from './core/auth/auth.service';
import { ROTULO_GRUPO } from './core/auth/perfis';
import { menuParaGrupos } from './core/menu';

/** Componente raiz (standalone): barra superior, menu filtrado por perfil e área de roteamento. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule, MatIconModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);

  /** Título exibido na barra superior. */
  protected readonly titulo = 'SISGARES';

  /** Itens de menu permitidos ao perfil do usuário (o backend continua autorizando cada chamada). */
  protected readonly menu = computed(() => menuParaGrupos(this.auth.grupos()));

  /** Descrição dos perfis do usuário para a barra superior. */
  protected readonly perfis = computed(() => this.auth.grupos().map((g) => ROTULO_GRUPO[g]).join(', '));

  protected sair(): void {
    this.auth.sair();
  }
}
