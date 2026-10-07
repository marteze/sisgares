package br.mp.mpf.sisgares.dominio;

/**
 * Estado de uma célula de 30 minutos da grade do Painel do Solicitante (Req. 15).
 */
public enum EstadoCelula {
    /** Horário disponível para reserva ("Reservar às XX:XX", Req. 15.9). */
    LIVRE,
    /** Célula cruza um Período ocupado do Ambiente ou de Ambiente_Relacionado (Req. 15.4). */
    OCUPADO,
    /** Célula não cruza o ocupado, mas cai na Margem de 30 minutos (Req. 15.6). */
    MARGEM,
    /** Início da célula já passou (Req. 15.7). */
    ULTRAPASSADO,
    /** Início da célula não atende à Antecedência_Mínima (Req. 15.8). */
    SEM_ANTECEDENCIA
}
