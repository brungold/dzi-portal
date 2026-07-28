/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.worker.polling;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.dzi.portal.worker.execution.TaskExecutor;

/**
 * Cienka pętla: fixedDelay czeka na koniec poprzedniego przebiegu, a drainQueue()
 * opróżnia kolejkę do dna — więc opóźnienie dotyczy tylko PUSTEJ kolejki,
 * kolejne zadania idą jedno po drugim bez czekania.
 */
@Component
@RequiredArgsConstructor
class TaskPoller {

    private final TaskExecutor taskExecutor;

    @Scheduled(fixedDelayString = "${portal.worker.poll-delay:5s}")
    void pollQueue() {
        taskExecutor.drainQueue();
    }
}
