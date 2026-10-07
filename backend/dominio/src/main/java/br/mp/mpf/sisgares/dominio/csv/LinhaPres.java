package br.mp.mpf.sisgares.dominio.csv;

import java.time.LocalDateTime;

/**
 * Linha de {@code dados-periodo-reserva.csv} (PRES_ID, PRES_RESE_ID, PRES_DTHR_INICIO, PRES_DTHR_TERMINO).
 * Datas/horas locais no fuso America/Fortaleza, formato {@code dd/MM/yyyy HH:mm:ss}.
 */
public record LinhaPres(long presId, long presReseId, LocalDateTime presDthrInicio, LocalDateTime presDthrTermino) {
}
