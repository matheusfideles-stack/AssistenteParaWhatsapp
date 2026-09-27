package com.assistente.parser;

import com.assistente.model.RecurrenceType;
import com.assistente.model.TaskPriority;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpretador local (sem IA externa, sem internet) de frases em portugues
 * para criacao de tarefas. Reconhece datas relativas/absolutas, horarios,
 * tempo relativo ("daqui 30 minutos"), recorrencia e prioridade.
 *
 * O algoritmo funciona removendo, uma a uma, as expressoes reconhecidas de
 * uma copia mutavel do texto; o que sobra depois de limpar conectores vira
 * o titulo da tarefa.
 */
public class TaskParser {

    private static final Pattern P_DEPOIS_DE_AMANHA =
            Pattern.compile("depois\\s+de\\s+amanh[ãa]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern P_AMANHA =
            Pattern.compile("\\bamanh[ãa]\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern P_HOJE =
            Pattern.compile("\\bhoje\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern P_PROXIMA_SEMANA =
            Pattern.compile("pr[óo]xima\\s+semana", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern P_PROXIMO_DIA_SEMANA = Pattern.compile(
            "pr[óo]xim[ao]\\s+(segunda|ter[çc]a|quarta|quinta|sexta|s[áa]bado|domingo)(-feira)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern P_DIA_SEMANA = Pattern.compile(
            "\\b(segunda|ter[çc]a|quarta|quinta|sexta|s[áa]bado|domingo)(-feira)?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern P_DAQUI_TEMPO = Pattern.compile(
            "daqui\\s+(a\\s+)?(\\d+|um|uma|meia)\\s*(minutos?|horas?|hora)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern P_DAQUI_MEIA_HORA = Pattern.compile(
            "daqui\\s+(a\\s+)?meia\\s+hora", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern P_TODO_DIA = Pattern.compile(
            "\\btodo(s)?\\s+(os\\s+)?dias?\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern P_TODA_SEMANA_DIA = Pattern.compile(
            "toda(s)?\\s+(as\\s+)?(segunda|ter[çc]a|quarta|quinta|sexta|s[áa]bado|domingo)s?(-feira)?s?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern P_TODO_MES_DIA = Pattern.compile(
            "todo\\s+m[eê]s\\s+(no\\s+)?dia\\s+(\\d{1,2})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern P_A_CADA = Pattern.compile(
            "a\\s+cada\\s+(\\d+)\\s*(minutos?|horas?)(\\s+at[ée]\\s+(eu\\s+)?conclu[ií]r)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern P_HORA_H = Pattern.compile(
            "\\b(\\d{1,2})\\s*h\\s*(\\d{2})?\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern P_HORA_DOISPONTOS = Pattern.compile(
            "\\b(\\d{1,2}):(\\d{2})\\b");
    private static final Pattern P_HORA_PERIODO = Pattern.compile(
            "\\b(\\d{1,2})\\s*(h(oras?)?)?\\s+da\\s+(manh[ãa]|tarde|noite)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern P_PERIODO_SOZINHO = Pattern.compile(
            "\\bde\\s+(manh[ãa]|tarde|noite)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern P_PRIORIDADE_ALTA = Pattern.compile(
            "prioridade\\s*:?\\s*alta", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern P_PRIORIDADE_BAIXA = Pattern.compile(
            "prioridade\\s*:?\\s*baixa", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern P_URGENTE = Pattern.compile("\\burgente\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    // \b sozinho so garante que o conector nao seja seguido por mais letras;
    // sem um \b tambem ANTES do conector, "avança[do]" perderia o sufixo "do"
    // como se fosse o conector solto "do". Por isso os dois lados tem \b.
    private static final Pattern P_LEADING_CONNECTORS = Pattern.compile(
            "^(\\s|,|;|-|:)*(\\b(às|as|de|do|da|em|no|na|para|pra|dia)\\b(\\s|,|;|-|:)*)+",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern P_TRAILING_CONNECTORS = Pattern.compile(
            "((\\s|,|;|-|:)*\\b(às|as|de|do|da|em|no|na|para|pra)\\b)+(\\s|,|;|-|:)*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    /** Interpreta o texto usando o instante atual como referencia. */
    public ParsedTask parse(String text) {
        return parse(text, LocalDateTime.now());
    }

    /** Sobrecarga usada nos testes, para que "hoje"/"amanha" sejam deterministicos. */
    public ParsedTask parse(String text, LocalDateTime now) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("O texto da tarefa nao pode estar vazio.");
        }
        ParsedTask result = new ParsedTask(text);
        String remaining = text.trim();
        LocalDate today = now.toLocalDate();

        // 1) Recorrencia (tambem define uma primeira data de ocorrencia)
        remaining = extractRecurrence(remaining, result, today);

        // 2) Tempo relativo: "daqui 30 minutos" -> define data e hora diretamente
        boolean relativeTimeFound;
        var daquiMatch = matchAny(remaining, P_DAQUI_MEIA_HORA, P_DAQUI_TEMPO);
        if (daquiMatch != null) {
            LocalDateTime target = resolveRelativeTime(daquiMatch, now);
            result.setDate(target.toLocalDate());
            result.setTime(target.toLocalTime());
            remaining = remove(remaining, daquiMatch);
            relativeTimeFound = true;
        } else {
            relativeTimeFound = false;
        }

        // 3) Datas absolutas/relativas por palavra (se ainda nao foi definida por recorrencia/tempo relativo)
        if (result.getDate() == null && !relativeTimeFound) {
            remaining = extractDate(remaining, result, today);
        }

        // 4) Horario (se ainda nao foi definido pelo "daqui X minutos")
        if (!relativeTimeFound) {
            remaining = extractTime(remaining, result);
        }

        // 5) Intervalo de repeticao do lembrete ("a cada 30 minutos até concluir")
        Matcher aCada = P_A_CADA.matcher(remaining);
        if (aCada.find()) {
            int value = Integer.parseInt(aCada.group(1));
            boolean horas = aCada.group(2).toLowerCase().startsWith("hora");
            result.setReminderIntervalMinutes(horas ? value * 60 : value);
            remaining = remove(remaining, aCada);
        }

        // 6) Prioridade
        remaining = extractPriority(remaining, result);

        // 7) Se nenhuma data foi encontrada, assume hoje (ou o proximo dia, se o horario ja passou)
        if (result.getDate() == null) {
            if (result.getTime() != null && result.getTime().isBefore(now.toLocalTime())) {
                result.setDate(today.plusDays(1));
            } else {
                result.setDate(today);
            }
        }

        // 8) Titulo = o que sobrou, limpo de conectores soltos
        result.setTitle(cleanTitle(remaining));

        return result;
    }

    // ---------------------------------------------------------------
    // Recorrencia
    // ---------------------------------------------------------------

    private String extractRecurrence(String remaining, ParsedTask result, LocalDate today) {
        Matcher mesDia = P_TODO_MES_DIA.matcher(remaining);
        if (mesDia.find()) {
            int day = Integer.parseInt(mesDia.group(2));
            result.setRecurrenceType(RecurrenceType.MONTHLY);
            result.setRecurrenceValue(String.valueOf(day));
            result.setDate(nextMonthlyOccurrence(today, day));
            return remove(remaining, mesDia);
        }

        Matcher todaSemana = P_TODA_SEMANA_DIA.matcher(remaining);
        if (todaSemana.find()) {
            DayOfWeek dow = parseWeekday(todaSemana.group(3));
            result.setRecurrenceType(RecurrenceType.WEEKLY);
            result.setRecurrenceValue(String.valueOf(dow.getValue()));
            result.setDate(resolveWeekday(dow, false, today));
            return remove(remaining, todaSemana);
        }

        Matcher todoDia = P_TODO_DIA.matcher(remaining);
        if (todoDia.find()) {
            result.setRecurrenceType(RecurrenceType.DAILY);
            result.setDate(today);
            return remove(remaining, todoDia);
        }

        return remaining;
    }

    private LocalDate nextMonthlyOccurrence(LocalDate today, int day) {
        YearMonth thisMonth = YearMonth.from(today);
        int clampedThis = Math.min(day, thisMonth.lengthOfMonth());
        LocalDate candidate = thisMonth.atDay(clampedThis);
        if (!candidate.isBefore(today)) {
            return candidate;
        }
        YearMonth nextMonth = thisMonth.plusMonths(1);
        int clampedNext = Math.min(day, nextMonth.lengthOfMonth());
        return nextMonth.atDay(clampedNext);
    }

    // ---------------------------------------------------------------
    // Datas
    // ---------------------------------------------------------------

    private String extractDate(String remaining, ParsedTask result, LocalDate today) {
        Matcher m;

        m = P_DEPOIS_DE_AMANHA.matcher(remaining);
        if (m.find()) {
            result.setDate(today.plusDays(2));
            return remove(remaining, m);
        }

        m = P_AMANHA.matcher(remaining);
        if (m.find()) {
            result.setDate(today.plusDays(1));
            return remove(remaining, m);
        }

        m = P_HOJE.matcher(remaining);
        if (m.find()) {
            result.setDate(today);
            return remove(remaining, m);
        }

        m = P_PROXIMA_SEMANA.matcher(remaining);
        if (m.find()) {
            result.setDate(today.plusDays(7));
            return remove(remaining, m);
        }

        m = P_PROXIMO_DIA_SEMANA.matcher(remaining);
        if (m.find()) {
            DayOfWeek dow = parseWeekday(m.group(1));
            result.setDate(resolveWeekday(dow, true, today));
            return remove(remaining, m);
        }

        m = P_DIA_SEMANA.matcher(remaining);
        if (m.find()) {
            DayOfWeek dow = parseWeekday(m.group(1));
            result.setDate(resolveWeekday(dow, false, today));
            return remove(remaining, m);
        }

        return remaining;
    }

    private DayOfWeek parseWeekday(String word) {
        String w = normalize(word);
        return switch (w) {
            case "segunda" -> DayOfWeek.MONDAY;
            case "terca" -> DayOfWeek.TUESDAY;
            case "quarta" -> DayOfWeek.WEDNESDAY;
            case "quinta" -> DayOfWeek.THURSDAY;
            case "sexta" -> DayOfWeek.FRIDAY;
            case "sabado" -> DayOfWeek.SATURDAY;
            case "domingo" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Dia da semana desconhecido: " + word);
        };
    }

    private String normalize(String s) {
        return s.toLowerCase()
                .replace("á", "a").replace("ã", "a")
                .replace("ç", "c").replace("é", "e")
                .replace("í", "i").replace("ó", "o");
    }

    /** Proxima data cujo dia da semana seja "target". */
    private LocalDate resolveWeekday(DayOfWeek target, boolean forceFuture, LocalDate today) {
        int diff = target.getValue() - today.getDayOfWeek().getValue();
        if (diff < 0) diff += 7;
        if (diff == 0 && forceFuture) diff = 7;
        return today.plusDays(diff);
    }

    // ---------------------------------------------------------------
    // Tempo relativo ("daqui X minutos/horas")
    // ---------------------------------------------------------------

    private LocalDateTime resolveRelativeTime(Matcher m, LocalDateTime now) {
        if (m.pattern() == P_DAQUI_MEIA_HORA) {
            return now.plusMinutes(30);
        }
        String qtyText = m.group(2).toLowerCase();
        int qty = switch (qtyText) {
            case "um", "uma" -> 1;
            case "meia" -> 30; // "daqui meia hora" tambem cai aqui se nao usar o padrao dedicado
            default -> Integer.parseInt(qtyText);
        };
        boolean horas = m.group(3).toLowerCase().startsWith("hora");
        return horas ? now.plusHours(qty) : now.plusMinutes(qty);
    }

    private Matcher matchAny(String text, Pattern... patterns) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                return m;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Horario
    // ---------------------------------------------------------------

    private String extractTime(String remaining, ParsedTask result) {
        Matcher m = P_HORA_PERIODO.matcher(remaining);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            String periodo = normalize(m.group(4));
            if (!periodo.equals("manha") && hour < 12) {
                hour += 12;
            }
            if (hour == 24) hour = 0;
            result.setTime(LocalTime.of(hour % 24, 0));
            return remove(remaining, m);
        }

        m = P_HORA_DOISPONTOS.matcher(remaining);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = Integer.parseInt(m.group(2));
            result.setTime(LocalTime.of(hour, minute));
            return remove(remaining, m);
        }

        m = P_HORA_H.matcher(remaining);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            if (hour <= 23 && minute <= 59) {
                result.setTime(LocalTime.of(hour, minute));
                return remove(remaining, m);
            }
        }

        m = P_PERIODO_SOZINHO.matcher(remaining);
        if (m.find()) {
            String periodo = normalize(m.group(1));
            LocalTime t = switch (periodo) {
                case "manha" -> LocalTime.of(8, 0);
                case "tarde" -> LocalTime.of(14, 0);
                case "noite" -> LocalTime.of(19, 0);
                default -> null;
            };
            if (t != null) {
                result.setTime(t);
                return remove(remaining, m);
            }
        }

        return remaining;
    }

    // ---------------------------------------------------------------
    // Prioridade
    // ---------------------------------------------------------------

    private String extractPriority(String remaining, ParsedTask result) {
        Matcher m = P_PRIORIDADE_ALTA.matcher(remaining);
        if (m.find()) {
            result.setPriority(TaskPriority.HIGH);
            remaining = remove(remaining, m);
        } else {
            m = P_PRIORIDADE_BAIXA.matcher(remaining);
            if (m.find()) {
                result.setPriority(TaskPriority.LOW);
                remaining = remove(remaining, m);
            } else if (P_URGENTE.matcher(remaining).find()) {
                result.setPriority(TaskPriority.HIGH);
                // "urgente" costuma fazer parte do sentido da tarefa; mantemos no titulo.
            }
        }
        return remaining;
    }

    // ---------------------------------------------------------------
    // Utilitarios
    // ---------------------------------------------------------------

    private String remove(String text, Matcher matcher) {
        return (text.substring(0, matcher.start()) + " " + text.substring(matcher.end())).trim();
    }

    private String cleanTitle(String remaining) {
        String title = remaining;
        String previous;
        do {
            previous = title;
            title = P_LEADING_CONNECTORS.matcher(title).replaceFirst("");
            title = P_TRAILING_CONNECTORS.matcher(title).replaceFirst("");
            title = title.replaceAll("\\s{2,}", " ").trim();
        } while (!title.equals(previous));

        title = title.replaceAll("^[,;:\\-\\s]+", "").replaceAll("[,;:\\-\\s]+$", "").trim();

        if (title.isEmpty()) {
            title = "Tarefa sem titulo";
        }
        return capitalize(title);
    }

    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
