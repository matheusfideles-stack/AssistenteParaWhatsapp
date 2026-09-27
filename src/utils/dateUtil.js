import dayjs from 'dayjs';

const WEEKDAYS = [
    'Domingo', 'Segunda-feira', 'Terça-feira', 'Quarta-feira',
    'Quinta-feira', 'Sexta-feira', 'Sábado',
];

const MONTHS = [
    'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
    'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro',
];

/** dayjs `day()`: 0=Domingo ... 6=Sabado. Usamos a mesma convencao no restante do app. */
export function weekdayName(dateStr) {
    return WEEKDAYS[dayjs(dateStr).day()];
}

export function monthName(month1to12) {
    return MONTHS[month1to12 - 1];
}

/** "Hoje", "Amanhã", "Ontem" ou dd/mm/aaaa. */
export function friendlyDate(dateStr) {
    const date = dayjs(dateStr).startOf('day');
    const today = dayjs().startOf('day');
    if (date.isSame(today, 'day')) return 'Hoje';
    if (date.isSame(today.add(1, 'day'), 'day')) return 'Amanhã';
    if (date.isSame(today.subtract(1, 'day'), 'day')) return 'Ontem';
    return date.format('DD/MM/YYYY');
}

/** Combina due_date (YYYY-MM-DD) + due_time (HH:mm) num dayjs, ou null se nao houver data. */
export function combineDateTime(dueDate, dueTime) {
    if (!dueDate) return null;
    const time = dueTime || '00:00';
    return dayjs(`${dueDate}T${time}`);
}

/** Ex: "01h 25min" ou "38min" ate um instante futuro. Retorna "atrasado" se ja passou. */
export function formatRemaining(target) {
    if (!target || !target.isValid() || target.isBefore(dayjs())) {
        return 'atrasado';
    }
    const totalMinutes = target.diff(dayjs(), 'minute');
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    if (hours > 0) {
        return `${String(hours).padStart(2, '0')}h ${String(minutes).padStart(2, '0')}min`;
    }
    return `${minutes}min`;
}

export function formatTime(dueTime) {
    return dueTime || '--:--';
}
