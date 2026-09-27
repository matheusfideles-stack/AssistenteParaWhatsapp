package com.assistente.backend;

import com.assistente.database.SqliteDatabaseManager;
import com.assistente.parser.TaskParser;
import com.assistente.repository.TaskRepository;
import com.assistente.service.BackupService;
import com.assistente.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandHandlerTest {

    private Path dbFile;
    private Path backupDir;
    private TaskService taskService;
    private CommandHandler handler;

    @BeforeEach
    void setUp() throws IOException {
        dbFile = Files.createTempFile("assistente-backend-cmd-", ".db");
        Files.delete(dbFile);
        SqliteDatabaseManager db = new SqliteDatabaseManager(dbFile);
        db.initializeSchema();
        TaskRepository repository = new TaskRepository(db);
        taskService = new TaskService(repository, new TaskParser());
        BackupService backupService = new BackupService(repository);
        backupDir = Files.createTempDirectory("assistente-backend-backup-");
        handler = new CommandHandler(taskService, backupService, backupDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(dbFile);
        Files.deleteIfExists(Path.of(dbFile + "-shm"));
        Files.deleteIfExists(Path.of(dbFile + "-wal"));
    }

    @Test
    void textoLivreCriaUmaTarefa() {
        CommandResult result = handler.handle("Hoje às 19h estudar Java", null);
        assertTrue(result.text().contains("Tarefa criada"));
        assertTrue(result.text().contains("Estudar Java"));
        assertEquals(1, taskService.findAll().size());
    }

    @Test
    void ajudaMostraMenuDeComandos() {
        CommandResult result = handler.handle("ajuda", null);
        assertTrue(result.text().toLowerCase().contains("comandos"));
    }

    @Test
    void tarefasListaTarefasDeHoje() {
        handler.handle("Hoje às 19h estudar Java", null);
        CommandResult result = handler.handle("tarefas", null);
        assertTrue(result.text().contains("TAREFAS DE HOJE"));
        assertTrue(result.text().contains("Estudar Java"));
    }

    @Test
    void listaVaziaInformaQueNaoHaTarefas() {
        CommandResult result = handler.handle("atrasadas", null);
        assertTrue(result.text().contains("Nenhuma tarefa encontrada"));
    }

    @Test
    void proximasListaTarefasDasProximas24Horas() {
        handler.handle("daqui 1 hora estudar Java", null);
        CommandResult result = handler.handle("proximas", null);
        assertTrue(result.text().contains("PRÓXIMAS 24H"));
        assertTrue(result.text().contains("Estudar Java"));
    }

    @Test
    void proximasNaoListaTarefaDaquiMaisDeUmDia() {
        handler.handle("Todo dia às 20h estudar Java", null);
        long id = taskService.findAll().get(0).getId();
        taskService.reschedule(id, LocalDate.now().plusDays(5), LocalTime.of(20, 0));
        CommandResult result = handler.handle("proximas", null);
        assertTrue(result.text().contains("Nenhuma tarefa encontrada"));
    }

    @Test
    void concluirPorIdMarcaComoConcluida() {
        handler.handle("Hoje às 19h estudar Java", null);
        long id = taskService.findAll().get(0).getId();
        CommandResult result = handler.handle("concluir #" + id, null);
        assertTrue(result.text().contains("concluída"));
        assertEquals("COMPLETED", taskService.get(id).getStatus().name());
    }

    @Test
    void concluirIdInexistenteRetornaErroAmigavel() {
        CommandResult result = handler.handle("concluir #999", null);
        assertTrue(result.text().contains("nao encontrada"));
    }

    @Test
    void cancelarPorIdMarcaComoCancelada() {
        handler.handle("Hoje às 19h estudar Java", null);
        long id = taskService.findAll().get(0).getId();
        CommandResult result = handler.handle("cancelar #" + id, null);
        assertTrue(result.text().contains("cancelada"));
        assertEquals("CANCELLED", taskService.get(id).getStatus().name());
    }

    @Test
    void adiarPorIdEMinutosReagendaTarefa() {
        handler.handle("Hoje às 19h estudar Java", null);
        long id = taskService.findAll().get(0).getId();
        CommandResult result = handler.handle("adiar #" + id + " 30", null);
        assertTrue(result.text().contains("adiada"));
        assertEquals(LocalTime.of(19, 30), taskService.get(id).getDueTime());
    }

    @Test
    void excluirPorIdRemoveTarefa() {
        handler.handle("Hoje às 19h estudar Java", null);
        long id = taskService.findAll().get(0).getId();
        handler.handle("excluir #" + id, null);
        assertTrue(taskService.find(id).isEmpty());
    }

    @Test
    void respostaNumerica1ConcluiLembreteAtivo() {
        handler.handle("Hoje às 19h estudar Java", null);
        long id = taskService.findAll().get(0).getId();
        CommandResult result = handler.handle("1", id);
        assertTrue(result.text().contains("concluída"));
        assertTrue(result.clearActiveReminder());
        assertEquals("COMPLETED", taskService.get(id).getStatus().name());
    }

    @Test
    void respostaNumerica2AdiaLembreteAtivoEm30Minutos() {
        handler.handle("Hoje às 19h estudar Java", null);
        long id = taskService.findAll().get(0).getId();
        handler.handle("2", id);
        assertEquals(LocalTime.of(19, 30), taskService.get(id).getDueTime());
    }

    @Test
    void respostaNumerica3CancelaLembreteAtivo() {
        handler.handle("Hoje às 19h estudar Java", null);
        long id = taskService.findAll().get(0).getId();
        handler.handle("3", id);
        assertEquals("CANCELLED", taskService.get(id).getStatus().name());
    }

    @Test
    void respostaPalavraConcluirConcluiLembreteAtivo() {
        handler.handle("Hoje às 19h estudar Java", null);
        long id = taskService.findAll().get(0).getId();
        CommandResult result = handler.handle("concluir", id);
        assertTrue(result.text().contains("concluída"));
        assertTrue(result.clearActiveReminder());
        assertEquals("COMPLETED", taskService.get(id).getStatus().name());
    }

    @Test
    void respostaPalavraAdiarAdiaLembreteAtivoEm30Minutos() {
        handler.handle("Hoje às 19h estudar Java", null);
        long id = taskService.findAll().get(0).getId();
        handler.handle("adiar", id);
        assertEquals(LocalTime.of(19, 30), taskService.get(id).getDueTime());
    }

    @Test
    void respostaPalavraCancelarCancelaLembreteAtivo() {
        handler.handle("Hoje às 19h estudar Java", null);
        long id = taskService.findAll().get(0).getId();
        handler.handle("cancelar", id);
        assertEquals("CANCELLED", taskService.get(id).getStatus().name());
    }

    @Test
    void concluirPorIdContinuaFuncionandoSemLembreteAtivo() {
        handler.handle("Hoje às 19h estudar Java", null);
        long id = taskService.findAll().get(0).getId();
        CommandResult result = handler.handle("concluir #" + id, null);
        assertTrue(result.text().contains("concluída"));
        assertEquals("COMPLETED", taskService.get(id).getStatus().name());
    }

    @Test
    void buscarEncontraPorTermo() {
        handler.handle("Hoje às 19h estudar Java avançado", null);
        CommandResult result = handler.handle("buscar Java", null);
        assertTrue(result.text().contains("RESULTADOS PARA"));
        assertTrue(result.text().contains("Java avançado"));
    }

    @Test
    void reagendarAlteraDataEHora() {
        handler.handle("Hoje às 19h estudar Java", null);
        long id = taskService.findAll().get(0).getId();
        LocalDate amanha = LocalDate.now().plusDays(1);
        String dataStr = "%02d/%02d/%d".formatted(amanha.getDayOfMonth(), amanha.getMonthValue(), amanha.getYear());
        CommandResult result = handler.handle("reagendar #" + id + " " + dataStr + " 08:00", null);
        assertTrue(result.text().contains("reagendada"));
        assertEquals(amanha, taskService.get(id).getDueDate());
        assertEquals(LocalTime.of(8, 0), taskService.get(id).getDueTime());
    }

    @Test
    void backupGeraArquivoParaEnviar() {
        handler.handle("Hoje às 19h estudar Java", null);
        CommandResult result = handler.handle("backup", null);
        assertTrue(result.text().contains("Backup exportado"));
        assertTrue(Files.exists(result.filePath()));
    }
}
