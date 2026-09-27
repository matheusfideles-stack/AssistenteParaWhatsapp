package com.assistente.parser;

import com.assistente.model.RecurrenceType;
import com.assistente.model.TaskPriority;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskParserTest {

    // Domingo, 27/09/2026, 10:00 - usado como "agora" fixo em todos os testes para determinismo.
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 27, 10, 0);

    private final TaskParser parser = new TaskParser();

    // ---------------------------------------------------------------
    // Datas
    // ---------------------------------------------------------------

    @Test
    void reconheceHojeComHorario() {
        ParsedTask r = parser.parse("Hoje às 19h estudar Java", NOW);
        assertEquals("Estudar Java", r.getTitle());
        assertEquals(NOW.toLocalDate(), r.getDate());
        assertEquals(LocalTime.of(19, 0), r.getTime());
    }

    @Test
    void reconheceAmanhaComHorario() {
        ParsedTask r = parser.parse("Amanhã às 14h fazer relatório", NOW);
        assertEquals("Fazer relatório", r.getTitle());
        assertEquals(NOW.toLocalDate().plusDays(1), r.getDate());
        assertEquals(LocalTime.of(14, 0), r.getTime());
    }

    @Test
    void reconheceDepoisDeAmanha() {
        ParsedTask r = parser.parse("Depois de amanhã às 8h dentista", NOW);
        assertEquals(NOW.toLocalDate().plusDays(2), r.getDate());
        assertEquals(LocalTime.of(8, 0), r.getTime());
    }

    @Test
    void reconheceDiaDaSemanaSimples() {
        ParsedTask r = parser.parse("Segunda às 9h reunião", NOW);
        LocalDate expected = NOW.toLocalDate();
        while (expected.getDayOfWeek() != DayOfWeek.MONDAY) {
            expected = expected.plusDays(1);
        }
        assertEquals(expected, r.getDate());
    }

    @Test
    void reconheceProximoDiaDaSemanaComoFuturo() {
        ParsedTask r = parser.parse("Próxima segunda às 9h reunião", NOW);
        LocalDate expected = NOW.toLocalDate().plusDays(1);
        while (expected.getDayOfWeek() != DayOfWeek.MONDAY) {
            expected = expected.plusDays(1);
        }
        assertEquals(expected, r.getDate());
        assertTrue(expected.isAfter(NOW.toLocalDate()));
    }

    @Test
    void reconheceProximaSemana() {
        ParsedTask r = parser.parse("Próxima semana entregar projeto", NOW);
        assertEquals(NOW.toLocalDate().plusDays(7), r.getDate());
    }

    @Test
    void semDataDefineHojeQuandoHorarioAindaNaoPassou() {
        ParsedTask r = parser.parse("Às 15:30 enviar o relatório", NOW);
        assertEquals(NOW.toLocalDate(), r.getDate());
        assertEquals(LocalTime.of(15, 30), r.getTime());
    }

    @Test
    void semDataEmpurraParaAmanhaQuandoHorarioJaPassou() {
        ParsedTask r = parser.parse("Às 08:00 tomar remédio", NOW); // NOW = 10:00
        assertEquals(NOW.toLocalDate().plusDays(1), r.getDate());
    }

    // ---------------------------------------------------------------
    // Horarios
    // ---------------------------------------------------------------

    @Test
    void reconheceHoraComH() {
        assertEquals(LocalTime.of(8, 0), parser.parse("8h academia", NOW).getTime());
        assertEquals(LocalTime.of(19, 30), parser.parse("19h30 estudar", NOW).getTime());
    }

    @Test
    void reconheceHoraComDoisPontos() {
        assertEquals(LocalTime.of(8, 30), parser.parse("8:30 academia", NOW).getTime());
    }

    @Test
    void reconhecePeriodosDoDia() {
        assertEquals(LocalTime.of(7, 0), parser.parse("7 da manhã reunião", NOW).getTime());
        assertEquals(LocalTime.of(15, 0), parser.parse("3 da tarde dentista", NOW).getTime());
        assertEquals(LocalTime.of(20, 0), parser.parse("8 da noite jantar", NOW).getTime());
    }

    @Test
    void reconhecePeriodoSemHoraEspecifica() {
        ParsedTask manha = parser.parse("Amanhã de manhã estudar programação", NOW);
        assertEquals(LocalTime.of(8, 0), manha.getTime());
        assertEquals("Estudar programação", manha.getTitle());

        assertEquals(LocalTime.of(14, 0), parser.parse("De tarde ligar pro banco", NOW).getTime());
        assertEquals(LocalTime.of(19, 0), parser.parse("De noite ler um livro", NOW).getTime());
    }

    // ---------------------------------------------------------------
    // Tempo relativo
    // ---------------------------------------------------------------

    @Test
    void reconheceDaquiMinutos() {
        ParsedTask r = parser.parse("Daqui 30 minutos ligar para João", NOW);
        LocalDateTime expected = NOW.plusMinutes(30);
        assertEquals(expected.toLocalDate(), r.getDate());
        assertEquals(expected.toLocalTime(), r.getTime());
        assertEquals("Ligar para João", r.getTitle());
    }

    @Test
    void reconheceDaquiHoras() {
        ParsedTask r = parser.parse("Daqui 2 horas revisar código", NOW);
        LocalDateTime expected = NOW.plusHours(2);
        assertEquals(expected.toLocalTime(), r.getTime());
    }

    @Test
    void reconheceDaquiMeiaHora() {
        ParsedTask r = parser.parse("Daqui meia hora pausa", NOW);
        assertEquals(NOW.plusMinutes(30).toLocalTime(), r.getTime());
    }

    @Test
    void reconheceDaquiUmaHora() {
        ParsedTask r = parser.parse("Daqui uma hora reunião", NOW);
        assertEquals(NOW.plusHours(1).toLocalTime(), r.getTime());
    }

    // ---------------------------------------------------------------
    // Recorrencia
    // ---------------------------------------------------------------

    @Test
    void reconheceRecorrenciaDiaria() {
        ParsedTask r = parser.parse("Todo dia às 20h estudar Java", NOW);
        assertEquals(RecurrenceType.DAILY, r.getRecurrenceType());
        assertEquals("Estudar Java", r.getTitle());
        assertEquals(LocalTime.of(20, 0), r.getTime());
        assertEquals(NOW.toLocalDate(), r.getDate());
    }

    @Test
    void reconheceRecorrenciaSemanal() {
        ParsedTask r = parser.parse("Toda segunda às 9h reunião", NOW);
        assertEquals(RecurrenceType.WEEKLY, r.getRecurrenceType());
        assertEquals(String.valueOf(DayOfWeek.MONDAY.getValue()), r.getRecurrenceValue());
    }

    @Test
    void reconheceRecorrenciaMensal() {
        ParsedTask r = parser.parse("Todo mês dia 5 pagar conta", NOW);
        assertEquals(RecurrenceType.MONTHLY, r.getRecurrenceType());
        assertEquals("5", r.getRecurrenceValue());
        assertEquals("Pagar conta", r.getTitle());
    }

    // ---------------------------------------------------------------
    // Prioridade e intervalo de repeticao
    // ---------------------------------------------------------------

    @Test
    void reconhecePrioridadeAlta() {
        ParsedTask r = parser.parse("Entregar projeto - prioridade alta", NOW);
        assertEquals(TaskPriority.HIGH, r.getPriority());
        assertEquals("Entregar projeto", r.getTitle());
    }

    @Test
    void reconhecePrioridadeBaixa() {
        ParsedTask r = parser.parse("Organizar gavetas - prioridade baixa", NOW);
        assertEquals(TaskPriority.LOW, r.getPriority());
    }

    @Test
    void urgenteViraAltaPrioridade() {
        ParsedTask r = parser.parse("Reunião urgente amanhã às 10h", NOW);
        assertEquals(TaskPriority.HIGH, r.getPriority());
    }

    @Test
    void prioridadeMediaPorPadrao() {
        ParsedTask r = parser.parse("Comprar pão", NOW);
        assertEquals(TaskPriority.MEDIUM, r.getPriority());
    }

    @Test
    void reconheceIntervaloDeRepeticao() {
        ParsedTask r = parser.parse("Beber água a cada 30 minutos até eu concluir", NOW);
        assertEquals(30, r.getReminderIntervalMinutes());
    }

    @Test
    void reconheceIntervaloDeRepeticaoEmHoras() {
        ParsedTask r = parser.parse("Alongar a cada 2 horas", NOW);
        assertEquals(120, r.getReminderIntervalMinutes());
    }

    // ---------------------------------------------------------------
    // Casos-limite
    // ---------------------------------------------------------------

    @Test
    void textoVazioLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("   ", NOW));
    }

    @Test
    void semTituloUsaValorPadrao() {
        ParsedTask r = parser.parse("Hoje às 19h", NOW);
        assertEquals("Tarefa sem titulo", r.getTitle());
    }

    @Test
    void naoTruncaTituloTerminadoComSufixoIgualAPalavraConectora() {
        // "avançado" termina em "do", que tambem e um conector ("do") - o titulo
        // nao pode perder esse sufixo achando que e o conector solto.
        ParsedTask r = parser.parse("Hoje às 19h estudar Java avançado", NOW);
        assertEquals("Estudar Java avançado", r.getTitle());
    }

    @Test
    void combinaRecorrenciaEHorarioEExemploCompleto() {
        ParsedTask r = parser.parse("Toda sexta às 18h organizar arquivos", NOW);
        assertEquals(RecurrenceType.WEEKLY, r.getRecurrenceType());
        assertEquals(String.valueOf(DayOfWeek.FRIDAY.getValue()), r.getRecurrenceValue());
        assertEquals(LocalTime.of(18, 0), r.getTime());
        assertEquals("Organizar arquivos", r.getTitle());
    }
}
