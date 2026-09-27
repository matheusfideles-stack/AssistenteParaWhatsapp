import dayjs from 'dayjs';

/**
 * Interpretador local (sem IA externa, sem internet) de frases em portugues
 * para criacao de tarefas via WhatsApp. Reconhece datas relativas/absolutas,
 * horarios, tempo relativo ("daqui 30 minutos"), recorrencia e prioridade.
 *
 * Mesma logica do TaskParser.java do Assistente Pessoal (desktop), portada
 * para JavaScript. Fronteiras de palavra usam lookaround customizado em vez
 * de \b, porque o \b padrao do JS (como o do Java) nao considera "ã", "õ"
 * etc. como caractere de palavra - isso quebraria "amanhã"/"manhã".
 */

const WORD = 'A-Za-zÀ-ÖØ-öø-ÿ0-9_';
const BEFORE = `(?<![${WORD}])`;
const AFTER = `(?![${WORD}])`;

const WEEKDAY_WORDS = 'segunda|ter[çc]a|quarta|quinta|sexta|s[áa]bado|domingo';
const WEEKDAY_TO_ISO = {
    segunda: 1, terca: 2, quarta: 3, quinta: 4, sexta: 5, sabado: 6, domingo: 7,
};

const P_DEPOIS_DE_AMANHA = new RegExp(`depois\\s+de\\s+amanh[ãa]`, 'i');
const P_AMANHA = new RegExp(`${BEFORE}amanh[ãa]${AFTER}`, 'i');
const P_HOJE = new RegExp(`${BEFORE}hoje${AFTER}`, 'i');
const P_PROXIMA_SEMANA = new RegExp(`pr[óo]xima\\s+semana`, 'i');
const P_PROXIMO_DIA_SEMANA = new RegExp(
    `pr[óo]xim[ao]\\s+(${WEEKDAY_WORDS})(-feira)?`, 'i');
const P_DIA_SEMANA = new RegExp(`${BEFORE}(${WEEKDAY_WORDS})(-feira)?${AFTER}`, 'i');

const P_DAQUI_MEIA_HORA = new RegExp(`daqui\\s+(a\\s+)?meia\\s+hora`, 'i');
const P_DAQUI_TEMPO = new RegExp(
    `daqui\\s+(a\\s+)?(\\d+|um|uma|meia)\\s*(minutos?|horas?|hora)${AFTER}`, 'i');

const P_TODO_DIA = new RegExp(`${BEFORE}todo(s)?\\s+(os\\s+)?dias?${AFTER}`, 'i');
const P_TODA_SEMANA_DIA = new RegExp(
    `toda(s)?\\s+(as\\s+)?(${WEEKDAY_WORDS})s?(-feira)?s?`, 'i');
const P_TODO_MES_DIA = new RegExp(`todo\\s+m[eê]s\\s+(no\\s+)?dia\\s+(\\d{1,2})`, 'i');

const P_A_CADA = new RegExp(
    `a\\s+cada\\s+(\\d+)\\s*(minutos?|horas?)(\\s+at[ée]\\s+(eu\\s+)?conclu[ií]r)?`, 'i');

const P_HORA_H = new RegExp(`${BEFORE}(\\d{1,2})\\s*h\\s*(\\d{2})?${AFTER}`, 'i');
const P_HORA_DOISPONTOS = new RegExp(`${BEFORE}(\\d{1,2}):(\\d{2})${AFTER}`);
const P_HORA_PERIODO = new RegExp(
    `${BEFORE}(\\d{1,2})\\s*(h(oras?)?)?\\s+da\\s+(manh[ãa]|tarde|noite)${AFTER}`, 'i');
const P_PERIODO_SOZINHO = new RegExp(`${BEFORE}de\\s+(manh[ãa]|tarde|noite)${AFTER}`, 'i');

const P_PRIORIDADE_ALTA = new RegExp(`prioridade\\s*:?\\s*alta`, 'i');
const P_PRIORIDADE_BAIXA = new RegExp(`prioridade\\s*:?\\s*baixa`, 'i');
const P_URGENTE = new RegExp(`${BEFORE}urgente${AFTER}`, 'i');

const CONNECTOR_WORDS = 'às|as|de|do|da|em|no|na|para|pra|dia';
// BEFORE aqui evita que um conector seja "encontrado" no fim de outra
// palavra (ex.: "avança*do*" nao pode perder o "do" como se fosse o
// conector "do" - sem essa checagem, o regex casava com o sufixo e
// truncava titulos legitimos).
const P_LEADING_CONNECTORS = new RegExp(
    `^(\\s|,|;|-|:)*(${BEFORE}(${CONNECTOR_WORDS})${AFTER}(\\s|,|;|-|:)*)+`, 'i');
const P_TRAILING_CONNECTORS = new RegExp(
    `((\\s|,|;|-|:)*${BEFORE}(${CONNECTOR_WORDS})${AFTER})+(\\s|,|;|-|:)*$`, 'i');

function normalize(word) {
    return word
        .toLowerCase()
        .replace('á', 'a').replace('ã', 'a')
        .replace('ç', 'c').replace('é', 'e')
        .replace('í', 'i').replace('ó', 'o');
}

function isoWeekdayFromWord(word) {
    const iso = WEEKDAY_TO_ISO[normalize(word)];
    if (!iso) throw new Error(`Dia da semana desconhecido: ${word}`);
    return iso;
}

/** Proxima data (dayjs) cujo dia ISO da semana seja "isoTarget" (1=Segunda...7=Domingo). */
function resolveWeekday(isoTarget, forceFuture, today) {
    const todayIso = today.day() === 0 ? 7 : today.day();
    let diff = isoTarget - todayIso;
    if (diff < 0) diff += 7;
    if (diff === 0 && forceFuture) diff = 7;
    return today.add(diff, 'day');
}

function nextMonthlyOccurrence(today, day) {
    const thisMonthLen = today.daysInMonth();
    const clampedThis = Math.min(day, thisMonthLen);
    const candidate = today.date(clampedThis);
    if (!candidate.isBefore(today, 'day')) {
        return candidate;
    }
    const nextMonth = today.add(1, 'month').date(1);
    const clampedNext = Math.min(day, nextMonth.daysInMonth());
    return nextMonth.date(clampedNext);
}

function remove(text, match) {
    const start = match.index;
    const end = start + match[0].length;
    return (text.slice(0, start) + ' ' + text.slice(end)).trim();
}

function cleanTitle(remaining) {
    let title = remaining;
    let previous;
    do {
        previous = title;
        title = title.replace(P_LEADING_CONNECTORS, '');
        title = title.replace(P_TRAILING_CONNECTORS, '');
        title = title.replace(/\s{2,}/g, ' ').trim();
    } while (title !== previous);

    title = title.replace(/^[,;:\-\s]+/, '').replace(/[,;:\-\s]+$/, '').trim();

    if (title === '') {
        title = 'Tarefa sem titulo';
    }
    return title.charAt(0).toUpperCase() + title.slice(1);
}

/**
 * Interpreta o texto e devolve:
 * { title, date: 'YYYY-MM-DD', time: 'HH:mm'|null, recurrenceType, recurrenceValue,
 *   reminderIntervalMinutes, priority }
 */
export function parseTask(text, now = dayjs()) {
    if (!text || !text.trim()) {
        throw new Error('O texto da tarefa não pode estar vazio.');
    }

    let remaining = text.trim();
    const today = now.startOf('day');

    let date = null;
    let time = null;
    let recurrenceType = 'NONE';
    let recurrenceValue = null;
    let reminderIntervalMinutes = null;
    let priority = 'MEDIUM';

    // 1) Recorrencia (tambem define a primeira data de ocorrencia)
    let m = P_TODO_MES_DIA.exec(remaining);
    if (m) {
        const day = parseInt(m[2], 10);
        recurrenceType = 'MONTHLY';
        recurrenceValue = String(day);
        date = nextMonthlyOccurrence(today, day);
        remaining = remove(remaining, m);
    } else if ((m = P_TODA_SEMANA_DIA.exec(remaining))) {
        const iso = isoWeekdayFromWord(m[3]);
        recurrenceType = 'WEEKLY';
        recurrenceValue = String(iso);
        date = resolveWeekday(iso, false, today);
        remaining = remove(remaining, m);
    } else if ((m = P_TODO_DIA.exec(remaining))) {
        recurrenceType = 'DAILY';
        date = today;
        remaining = remove(remaining, m);
    }

    // 2) Tempo relativo: "daqui 30 minutos" -> define data e hora diretamente
    let relativeTimeFound = false;
    m = P_DAQUI_MEIA_HORA.exec(remaining);
    if (!m) m = P_DAQUI_TEMPO.exec(remaining);
    if (m) {
        let target;
        if (m[0].toLowerCase().includes('meia') && !/\d/.test(m[0])) {
            target = now.add(30, 'minute');
        } else {
            const qtyText = (m[2] || '').toLowerCase();
            let qty;
            if (qtyText === 'um' || qtyText === 'uma') qty = 1;
            else if (qtyText === 'meia') qty = 30;
            else qty = parseInt(qtyText, 10);
            const horas = (m[3] || '').toLowerCase().startsWith('hora');
            target = horas ? now.add(qty, 'hour') : now.add(qty, 'minute');
        }
        date = target.startOf('day');
        time = target.format('HH:mm');
        remaining = remove(remaining, m);
        relativeTimeFound = true;
    }

    // 3) Datas absolutas/relativas por palavra (se ainda nao definida)
    if (!date && !relativeTimeFound) {
        remaining = extractDate(remaining, today, (d) => { date = d; });
    }

    // 4) Horario (se ainda nao definido pelo "daqui X")
    if (!relativeTimeFound) {
        remaining = extractTime(remaining, (t) => { time = t; });
    }

    // 5) Intervalo de repeticao do lembrete ("a cada 30 minutos ate concluir")
    m = P_A_CADA.exec(remaining);
    if (m) {
        const value = parseInt(m[1], 10);
        const horas = m[2].toLowerCase().startsWith('hora');
        reminderIntervalMinutes = horas ? value * 60 : value;
        remaining = remove(remaining, m);
    }

    // 6) Prioridade
    m = P_PRIORIDADE_ALTA.exec(remaining);
    if (m) {
        priority = 'HIGH';
        remaining = remove(remaining, m);
    } else if ((m = P_PRIORIDADE_BAIXA.exec(remaining))) {
        priority = 'LOW';
        remaining = remove(remaining, m);
    } else if (P_URGENTE.test(remaining)) {
        priority = 'HIGH';
        // "urgente" normalmente faz parte do sentido da tarefa; mantido no titulo.
    }

    // 7) Se nenhuma data foi encontrada, assume hoje (ou amanha, se o horario ja passou)
    if (!date) {
        if (time && time < now.format('HH:mm')) {
            date = today.add(1, 'day');
        } else {
            date = today;
        }
    }

    return {
        title: cleanTitle(remaining),
        date: date.format('YYYY-MM-DD'),
        time,
        recurrenceType,
        recurrenceValue,
        reminderIntervalMinutes,
        priority,
    };
}

function extractDate(remaining, today, setDate) {
    let m = P_DEPOIS_DE_AMANHA.exec(remaining);
    if (m) {
        setDate(today.add(2, 'day'));
        return remove(remaining, m);
    }
    m = P_AMANHA.exec(remaining);
    if (m) {
        setDate(today.add(1, 'day'));
        return remove(remaining, m);
    }
    m = P_HOJE.exec(remaining);
    if (m) {
        setDate(today);
        return remove(remaining, m);
    }
    m = P_PROXIMA_SEMANA.exec(remaining);
    if (m) {
        setDate(today.add(7, 'day'));
        return remove(remaining, m);
    }
    m = P_PROXIMO_DIA_SEMANA.exec(remaining);
    if (m) {
        const iso = isoWeekdayFromWord(m[1]);
        setDate(resolveWeekday(iso, true, today));
        return remove(remaining, m);
    }
    m = P_DIA_SEMANA.exec(remaining);
    if (m) {
        const iso = isoWeekdayFromWord(m[1]);
        setDate(resolveWeekday(iso, false, today));
        return remove(remaining, m);
    }
    return remaining;
}

function extractTime(remaining, setTime) {
    let m = P_HORA_PERIODO.exec(remaining);
    if (m) {
        let hour = parseInt(m[1], 10);
        const periodo = normalize(m[4]);
        if (periodo !== 'manha' && hour < 12) hour += 12;
        if (hour === 24) hour = 0;
        setTime(`${String(hour % 24).padStart(2, '0')}:00`);
        return remove(remaining, m);
    }
    m = P_HORA_DOISPONTOS.exec(remaining);
    if (m) {
        const hour = parseInt(m[1], 10);
        const minute = parseInt(m[2], 10);
        setTime(`${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`);
        return remove(remaining, m);
    }
    m = P_HORA_H.exec(remaining);
    if (m) {
        const hour = parseInt(m[1], 10);
        const minute = m[2] ? parseInt(m[2], 10) : 0;
        if (hour <= 23 && minute <= 59) {
            setTime(`${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`);
            return remove(remaining, m);
        }
    }
    m = P_PERIODO_SOZINHO.exec(remaining);
    if (m) {
        const periodo = normalize(m[1]);
        const table = { manha: '08:00', tarde: '14:00', noite: '19:00' };
        if (table[periodo]) {
            setTime(table[periodo]);
            return remove(remaining, m);
        }
    }
    return remaining;
}
