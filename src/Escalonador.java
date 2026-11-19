import java.util.ArrayList;

// Simulador de escalonamento Round Robin com Feedback
// Filas: alta, baixa e ioLista (processos esperando I/O)
// prioridade: 1 = alta, 2 = baixa
// tipoIo: 0 = nenhum, 1 = disco, 2 = fita, 3 = impressora

public class Escalonador {

    // PARTE 2 - PCB do processo
    static class Processo {
        int pid;
        int ppid;           // 0 = processo inicial
        int tempoTotal;     // tempo de cpu que ele precisa
        int tempoRestante;  // quanto ainda falta
        int cpuUsada;       // quanto ja usou
        int tipoIo;         // 0=nenhum, 1=disco, 2=fita, 3=impressora
        int tempoIo;        // quanto tempo o I/O demora
        int momentoIo;      // pede o I/O depois de usar essa quantidade de cpu
        int prioridade;     // 1=alta, 2=baixa
        int fim;            // tempo em que terminou
        String status;      // NOVO, PRONTO, EXECUTANDO, BLOQUEADO, FINALIZADO
        boolean ioFeito;    // se ja fez o I/O

        public Processo(int pid, int ppid, int tempo, int tipoIo, int tempoIo, int momentoIo) {
            this.pid = pid;
            this.ppid = ppid;
            this.tempoTotal = tempo;
            this.tempoRestante = tempo;
            this.cpuUsada = 0;
            this.tipoIo = tipoIo;
            this.tempoIo = tempoIo;
            this.momentoIo = momentoIo;
            this.prioridade = 1;
            this.status = "NOVO";
            this.ioFeito = false;
        }
    }

    // processo que esta esperando o I/O e quanto tempo falta
    static class IoItem {
        Processo p;
        int espera;

        public IoItem(Processo p, int espera) {
            this.p = p;
            this.espera = espera;
        }
    }

    static String[] nomesIo = {"NENHUM", "DISCO", "FITA", "IMPRESSORA"};

    // PARTE 6 - passa 1 unidade de tempo no I/O
    // quem terminou volta pra fila: disco vai pra baixa, fita e impressora vao pra alta
    static void atualizarIo(ArrayList<IoItem> ioLista, ArrayList<Processo> alta, ArrayList<Processo> baixa, int tempo) {
        // primeiro desconta 1 de todo mundo e guarda quem terminou
        ArrayList<IoItem> terminaram = new ArrayList<>();
        for (IoItem item : ioLista) {
            item.espera--;
            if (item.espera == 0) {
                terminaram.add(item);
            }
        }

        // depois tira da fila de I/O e manda pra fila certa
        for (IoItem item : terminaram) {
            ioLista.remove(item);
            Processo p = item.p;
            p.status = "PRONTO";
            if (p.tipoIo == 1) {
                p.prioridade = 2;
                baixa.add(p);
                System.out.printf("[t=%03d] P%d retornou do DISCO -> fila BAIXA\n", tempo, p.pid);
            } else {
                p.prioridade = 1;
                alta.add(p);
                System.out.printf("[t=%03d] P%d retornou da %s -> fila ALTA\n", tempo, p.pid, nomesIo[p.tipoIo]);
            }
        }
    }

    public static void main(String[] args) {

        // PARTE 3 - filas e inicializacao
        int quantum = 3;
        int total = 5;

        System.out.printf("[config] quantum=%d | processos=%d | valores fixos\n", quantum, total);

        ArrayList<Processo> alta = new ArrayList<>();
        ArrayList<Processo> baixa = new ArrayList<>();
        ArrayList<IoItem> ioLista = new ArrayList<>();

        // pid, ppid, cpu, tipoIo, tempoIo, momentoIo
        alta.add(new Processo(1, 0, 6, 0, 0, -1));
        alta.add(new Processo(2, 0, 5, 1, 4, 2)); // disco
        alta.add(new Processo(3, 0, 4, 2, 3, 1)); // fita
        alta.add(new Processo(4, 0, 3, 3, 5, 1)); // impressora
        alta.add(new Processo(5, 0, 2, 0, 0, -1));

        // copia da lista pra mostrar o resumo no final
        ArrayList<Processo> todos = new ArrayList<>(alta);

        int tempo = 0;
        int finalizados = 0;
        int preempcoes = 0;
        int ociosa = 0;
        int qtdDisco = 0, qtdFita = 0, qtdImpressora = 0;

        for (Processo p : alta) {
            p.status = "PRONTO";
            System.out.printf("[t=%03d] P%d criado -> fila ALTA\n", tempo, p.pid);
        }

        // PARTE 4 - loop principal
        while (finalizados < total) {

            // escolhe o processo: primeiro a fila alta, depois a baixa
            Processo atual;
            if (!alta.isEmpty()) {
                atual = alta.remove(0);
            } else if (!baixa.isEmpty()) {
                atual = baixa.remove(0);
            } else {
                // ninguem pronto, cpu fica parada
                System.out.printf("[t=%03d] CPU ociosa\n", tempo);
                ociosa++;
                tempo++;
                atualizarIo(ioLista, alta, baixa, tempo);
                continue;
            }

            // roda no maximo 1 quantum (ou o que falta, se for menos)
            int exec = Math.min(quantum, atual.tempoRestante);
            boolean pediuIo = false;

            // se o I/O vem antes do fim do quantum, roda so ate ele
            if (!atual.ioFeito && atual.tipoIo > 0 && atual.momentoIo - atual.cpuUsada <= exec) {
                exec = atual.momentoIo - atual.cpuUsada;
                pediuIo = true;
            }

            atual.status = "EXECUTANDO";
            System.out.printf("[t=%03d] CPU executa P%d por %d unidades\n", tempo, atual.pid, exec);

            // o tempo anda de 1 em 1 pra o I/O dos outros ir andando junto
            for (int i = 0; i < exec; i++) {
                tempo++;
                atualizarIo(ioLista, alta, baixa, tempo);
            }
            atual.tempoRestante = atual.tempoRestante - exec;
            atual.cpuUsada = atual.cpuUsada + exec;

            // depois de sair da cpu: pediu I/O, terminou ou sofreu preempcao
            if (pediuIo) {
                // PARTE 6 - vai pra fila de I/O
                atual.ioFeito = true;
                atual.status = "BLOQUEADO";
                ioLista.add(new IoItem(atual, atual.tempoIo));
                if (atual.tipoIo == 1) qtdDisco++;
                if (atual.tipoIo == 2) qtdFita++;
                if (atual.tipoIo == 3) qtdImpressora++;
                System.out.printf("[t=%03d] P%d solicitou I/O %s por %d unidades\n", tempo, atual.pid, nomesIo[atual.tipoIo], atual.tempoIo);
            } else if (atual.tempoRestante == 0) {
                // terminou
                finalizados++;
                atual.status = "FINALIZADO";
                atual.fim = tempo;
                System.out.printf("[t=%03d] P%d finalizado\n", tempo, atual.pid);
            } else {
                // PARTE 5 - preempcao e feedback
                // usou o quantum todo e nao terminou: sai da cpu e cai pra fila baixa
                atual.prioridade = 2;
                atual.status = "PRONTO";
                baixa.add(atual);
                preempcoes++;
                System.out.printf("[t=%03d] P%d sofreu preempcao -> fila BAIXA\n", tempo, atual.pid);
            }
        }

        System.out.printf("[fim] Processos finalizados: %d/%d | tempo total: %d\n", finalizados, total, tempo);

        // resumo: espera = tempo que ficou parado na fila (fim - cpu - I/O)
        System.out.println();
        System.out.println("Resumo:");
        int somaEspera = 0;
        int somaFim = 0;
        for (Processo p : todos) {
            int espera = p.fim - p.tempoTotal;
            if (p.ioFeito) {
                espera = espera - p.tempoIo;
            }
            somaEspera = somaEspera + espera;
            somaFim = somaFim + p.fim;
            System.out.printf("P%d (ppid %d) - %s - terminou em t=%d - esperou %d\n", p.pid, p.ppid, p.status, p.fim, espera);
        }
        System.out.println("Preempcoes: " + preempcoes);
        System.out.println("I/O: disco=" + qtdDisco + ", fita=" + qtdFita + ", impressora=" + qtdImpressora);
        System.out.printf("Espera media: %.1f\n", (double) somaEspera / total);
        System.out.printf("Turnaround medio: %.1f\n", (double) somaFim / total);
        System.out.println("CPU ociosa: " + ociosa + " unidades");
    }
}
