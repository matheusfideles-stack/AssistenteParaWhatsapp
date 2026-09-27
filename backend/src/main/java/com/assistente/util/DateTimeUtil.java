package com.assistente.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Formatacao de datas/horas em portugues, usada pela interface. */
public final class DateTimeUtil {

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static final String[] WEEKDAYS = {
            "Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira",
            "Sexta-feira", "Sábado", "Domingo"
    };

    private static final String[] MONTHS = {
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    };

    private DateTimeUtil() {
    }

    public static String weekdayName(LocalDate date) {
        return WEEKDAYS[date.getDayOfWeek().getValue() - 1];
    }

    public static String monthName(int month1to12) {
        return MONTHS[month1to12 - 1];
    }

    public static String friendlyDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return "Hoje";
        if (date.equals(today.plusDays(1))) return "Amanhã";
        if (date.equals(today.minusDays(1))) return "Ontem";
        return date.format(DATE_FMT);
    }

    /** Ex: "01h 25min" ou "38min" para um intervalo ate um instante futuro. */
    public static String remaining(LocalDateTime now, LocalDateTime target) {
        if (target == null || target.isBefore(now)) {
            return "atrasado";
        }
        Duration d = Duration.between(now, target);
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        if (hours > 0) {
            return "%02dh %02dmin".formatted(hours, minutes);
        }
        return "%dmin".formatted(minutes);
    }
}
