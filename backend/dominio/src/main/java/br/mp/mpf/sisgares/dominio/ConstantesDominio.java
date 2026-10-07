package br.mp.mpf.sisgares.dominio;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

/**
 * Constantes gerais do Núcleo_Domínio.
 */
public final class ConstantesDominio {

    /** Fuso oficial de todas as datas do SISGARES (Req. 1.5). */
    public static final ZoneId ZONA = ZoneId.of("America/Fortaleza");

    /** Margem entre reservas do mesmo ambiente ou de ambientes relacionados (RN5/RN6). */
    public static final Duration MARGEM = Duration.ofMinutes(30);

    /** Limite máximo da Antecedência_Mínima em minutos (7 dias, Req. 8.1). */
    public static final int ANTECEDENCIA_MAXIMA_MINUTOS = 10_080;

    private ConstantesDominio() {
    }

    /** Relógio de produção no fuso oficial; nos testes, injete {@code Clock.fixed(...)} (Req. 22.7). */
    public static Clock relogioPadrao() {
        return Clock.system(ZONA);
    }
}
