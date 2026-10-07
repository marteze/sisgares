import type { ReactNode } from 'react';
import type { Reserva } from '../types';
import { formatarDataHora, iconeDisposicao, iconeRecurso } from '../utils/format';
import { StatusBadge } from './StatusBadge';
import { ImagemAmpliavel } from './ImagemAmpliavel';

interface Props {
  reserva: Reserva;
  acoes?: ReactNode;
}

export function ReservaCard({ reserva, acoes }: Props) {
  return (
    <article className="card" aria-labelledby={`r-${reserva.RESE_ID}`}>
      <header className="card__head">
        <h3 id={`r-${reserva.RESE_ID}`} className="card__title">
          {reserva.RESE_FINALIDADE}
        </h3>
        <StatusBadge status={reserva.status} />
      </header>

      <dl className="card__meta">
        <div>
          <dt>Solicitante</dt>
          <dd>{reserva.RESE_SOLICITANTE_NOME}</dd>
        </div>
        <div>
          <dt>Ambiente</dt>
          <dd>
            {reserva.ambienteDescricao ??
              `Local próprio: ${reserva.RESE_COMPLEMENTO ?? '—'}`}
            {reserva.disposicaoDescricao ? ` · ${reserva.disposicaoDescricao}` : ''}
            {reserva.disposicaoIcone && (
              <ImagemAmpliavel
                className="card__disp"
                src={iconeDisposicao(reserva.disposicaoIcone)}
                alt={`Disposição: ${reserva.disposicaoDescricao ?? ''}`}
                width={80}
              />
            )}
          </dd>
        </div>
        <div>
          <dt>Participantes</dt>
          <dd>{reserva.RESE_PARTICIPANTES}</dd>
        </div>
        <div>
          <dt>Períodos</dt>
          <dd>
            <ul className="lista-limpa">
              {reserva.periodos.map((p) => (
                <li key={p.PRES_ID}>
                  {formatarDataHora(p.PRES_DTHR_INICIO)} —{' '}
                  {formatarDataHora(p.PRES_DTHR_TERMINO)}
                </li>
              ))}
            </ul>
          </dd>
        </div>
      </dl>

      {reserva.recursos.length > 0 && (
        <div className="card__recursos">
          <h4 className="sr-sublabel">Recursos e serviços</h4>
          <ul className="chips" aria-label="Recursos solicitados">
            {reserva.recursos.map((r) => (
              <li key={r.recursoId} className="chip">
                <img src={iconeRecurso(r.icone)} alt="" width={20} height={20} />
                <span>
                  {r.descricao}
                  {r.quantidade ? ` (${r.quantidade})` : ''}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {reserva.snps.length > 0 && (
        <p className="card__setores">
          <strong>SNP:</strong>{' '}
          {reserva.snps.map((s, i) => (
            <span key={s.SNP_ID}>
              {i > 0 && ', '}
              <a href={s.SNP_LINK} target="_blank" rel="noreferrer">
                {s.SNP_CODIGO}
              </a>
            </span>
          ))}
        </p>
      )}

      {reserva.setoresEnvolvidos.length > 0 && (
        <p className="card__setores">
          <strong>Setores notificados:</strong>{' '}
          {reserva.setoresEnvolvidos.map((s) => s.nome).join(', ')}
        </p>
      )}

      <p className="card__alt">
        <small>
          Última alteração: {formatarDataHora(reserva.RESE_DTHR_ALTERACAO)}
        </small>
      </p>

      {acoes && <footer className="card__acoes">{acoes}</footer>}
    </article>
  );
}
