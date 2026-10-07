import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';

/** Placeholder para telas ainda não implementadas; o título vem de `data.titulo` da rota. */
@Component({
  selector: 'app-em-construcao',
  template: `
    <section class="em-construcao" aria-labelledby="titulo-pagina">
      <h2 id="titulo-pagina" tabindex="-1">{{ titulo() }}</h2>
      <p>Esta tela ainda está em construção.</p>
    </section>
  `,
  styles: `
    .em-construcao { padding: 1rem; }
  `,
})
export class EmConstrucao {
  protected readonly titulo = toSignal(
    inject(ActivatedRoute).data.pipe(map((d) => String(d['titulo'] ?? 'Em construção'))),
    { initialValue: 'Em construção' },
  );
}
