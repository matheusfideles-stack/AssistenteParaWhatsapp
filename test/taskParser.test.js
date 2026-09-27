import { test } from 'node:test';
import assert from 'node:assert/strict';
import dayjs from 'dayjs';
import { parseTask } from '../src/parser/taskParser.js';

// Domingo, 27/09/2026, 10:00 - usado como "agora" fixo em todos os testes.
const NOW = dayjs('2026-09-27T10:00:00');

test('reconhece hoje com horario', () => {
    const r = parseTask('Hoje às 19h estudar Java', NOW);
    assert.equal(r.title, 'Estudar Java');
    assert.equal(r.date, NOW.format('YYYY-MM-DD'));
    assert.equal(r.time, '19:00');
});

test('reconhece amanha com horario', () => {
    const r = parseTask('Amanhã às 14h fazer relatório', NOW);
    assert.equal(r.title, 'Fazer relatório');
    assert.equal(r.date, NOW.add(1, 'day').format('YYYY-MM-DD'));
    assert.equal(r.time, '14:00');
});

test('reconhece depois de amanha', () => {
    const r = parseTask('Depois de amanhã às 8h dentista', NOW);
    assert.equal(r.date, NOW.add(2, 'day').format('YYYY-MM-DD'));
    assert.equal(r.time, '08:00');
});

test('reconhece dia da semana simples (inclui hoje se for o mesmo dia)', () => {
    const r = parseTask('Segunda às 9h reunião', NOW);
    let expected = NOW;
    while (expected.day() !== 1) expected = expected.add(1, 'day'); // 1 = segunda
    assert.equal(r.date, expected.format('YYYY-MM-DD'));
});

test('proximo dia da semana forca o futuro', () => {
    const r = parseTask('Próxima segunda às 9h reunião', NOW);
    let expected = NOW.add(1, 'day');
    while (expected.day() !== 1) expected = expected.add(1, 'day');
    assert.equal(r.date, expected.format('YYYY-MM-DD'));
    assert.ok(dayjs(r.date).isAfter(NOW, 'day'));
});

test('reconhece proxima semana', () => {
    const r = parseTask('Próxima semana entregar projeto', NOW);
    assert.equal(r.date, NOW.add(7, 'day').format('YYYY-MM-DD'));
});

test('sem data define hoje quando horario ainda nao passou', () => {
    const r = parseTask('Às 15:30 enviar o relatório', NOW);
    assert.equal(r.date, NOW.format('YYYY-MM-DD'));
    assert.equal(r.time, '15:30');
});

test('sem data empurra para amanha quando horario ja passou', () => {
    const r = parseTask('Às 08:00 tomar remédio', NOW); // NOW = 10:00
    assert.equal(r.date, NOW.add(1, 'day').format('YYYY-MM-DD'));
});

test('reconhece hora com h', () => {
    assert.equal(parseTask('8h academia', NOW).time, '08:00');
    assert.equal(parseTask('19h30 estudar', NOW).time, '19:30');
});

test('reconhece hora com dois pontos', () => {
    assert.equal(parseTask('8:30 academia', NOW).time, '08:30');
});

test('reconhece periodos do dia com hora especifica', () => {
    assert.equal(parseTask('7 da manhã reunião', NOW).time, '07:00');
    assert.equal(parseTask('3 da tarde dentista', NOW).time, '15:00');
    assert.equal(parseTask('8 da noite jantar', NOW).time, '20:00');
});

test('reconhece periodo sem hora especifica', () => {
    const manha = parseTask('Amanhã de manhã estudar programação', NOW);
    assert.equal(manha.time, '08:00');
    assert.equal(manha.title, 'Estudar programação');

    assert.equal(parseTask('De tarde ligar pro banco', NOW).time, '14:00');
    assert.equal(parseTask('De noite ler um livro', NOW).time, '19:00');
});

test('reconhece daqui minutos', () => {
    const r = parseTask('Daqui 30 minutos ligar para João', NOW);
    const expected = NOW.add(30, 'minute');
    assert.equal(r.date, expected.format('YYYY-MM-DD'));
    assert.equal(r.time, expected.format('HH:mm'));
    assert.equal(r.title, 'Ligar para João');
});

test('reconhece daqui horas', () => {
    const r = parseTask('Daqui 2 horas revisar código', NOW);
    assert.equal(r.time, NOW.add(2, 'hour').format('HH:mm'));
});

test('reconhece daqui meia hora', () => {
    const r = parseTask('Daqui meia hora pausa', NOW);
    assert.equal(r.time, NOW.add(30, 'minute').format('HH:mm'));
});

test('reconhece daqui uma hora', () => {
    const r = parseTask('Daqui uma hora reunião', NOW);
    assert.equal(r.time, NOW.add(1, 'hour').format('HH:mm'));
});

test('reconhece recorrencia diaria', () => {
    const r = parseTask('Todo dia às 20h estudar Java', NOW);
    assert.equal(r.recurrenceType, 'DAILY');
    assert.equal(r.title, 'Estudar Java');
    assert.equal(r.time, '20:00');
    assert.equal(r.date, NOW.format('YYYY-MM-DD'));
});

test('reconhece recorrencia semanal', () => {
    const r = parseTask('Toda segunda às 9h reunião', NOW);
    assert.equal(r.recurrenceType, 'WEEKLY');
    assert.equal(r.recurrenceValue, '1'); // 1 = segunda (ISO)
});

test('reconhece recorrencia mensal', () => {
    const r = parseTask('Todo mês dia 5 pagar conta', NOW);
    assert.equal(r.recurrenceType, 'MONTHLY');
    assert.equal(r.recurrenceValue, '5');
    assert.equal(r.title, 'Pagar conta');
});

test('reconhece prioridade alta', () => {
    const r = parseTask('Entregar projeto - prioridade alta', NOW);
    assert.equal(r.priority, 'HIGH');
    assert.equal(r.title, 'Entregar projeto');
});

test('reconhece prioridade baixa', () => {
    const r = parseTask('Organizar gavetas - prioridade baixa', NOW);
    assert.equal(r.priority, 'LOW');
});

test('urgente vira alta prioridade', () => {
    const r = parseTask('Reunião urgente amanhã às 10h', NOW);
    assert.equal(r.priority, 'HIGH');
});

test('prioridade media por padrao', () => {
    const r = parseTask('Comprar pão', NOW);
    assert.equal(r.priority, 'MEDIUM');
});

test('reconhece intervalo de repeticao em minutos', () => {
    const r = parseTask('Beber água a cada 30 minutos até eu concluir', NOW);
    assert.equal(r.reminderIntervalMinutes, 30);
});

test('reconhece intervalo de repeticao em horas', () => {
    const r = parseTask('Alongar a cada 2 horas', NOW);
    assert.equal(r.reminderIntervalMinutes, 120);
});

test('texto vazio lanca excecao', () => {
    assert.throws(() => parseTask('   ', NOW));
});

test('sem titulo usa valor padrao', () => {
    const r = parseTask('Hoje às 19h', NOW);
    assert.equal(r.title, 'Tarefa sem titulo');
});

test('nao trunca titulo terminado com sufixo igual a uma palavra conectora', () => {
    // "avançado" termina em "do", que tambem e um conector ("do") - o title
    // nao pode perder esse sufixo achando que e o conector solto.
    const r = parseTask('Hoje às 19h estudar Java avançado', NOW);
    assert.equal(r.title, 'Estudar Java avançado');
});

test('combina recorrencia e horario num exemplo completo', () => {
    const r = parseTask('Toda sexta às 18h organizar arquivos', NOW);
    assert.equal(r.recurrenceType, 'WEEKLY');
    assert.equal(r.recurrenceValue, '5'); // 5 = sexta (ISO)
    assert.equal(r.time, '18:00');
    assert.equal(r.title, 'Organizar arquivos');
});
