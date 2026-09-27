package com.assistente.model;

/**
 * Tipo de recorrencia de uma tarefa.
 * O campo recurrenceValue de {@link Task} guarda o detalhe de cada tipo:
 *  - NONE:    nao usado
 *  - DAILY:   nao usado (repete todo dia)
 *  - WEEKLY:  numero do dia da semana (1=Segunda ... 7=Domingo), ex: "1"
 *  - MONTHLY: dia do mes (1-31), ex: "5"
 */
public enum RecurrenceType {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
}
