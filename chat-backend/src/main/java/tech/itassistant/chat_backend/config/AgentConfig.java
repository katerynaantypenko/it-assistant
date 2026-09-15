package tech.itassistant.chat_backend.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@Log4j2
public class AgentConfig {

    /**
     * An agent run does not happen on the request thread: the POST that starts it returns the SSE
     * stream immediately, so that the approval POST for the same run can still be served.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService agentExecutor() {
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("agent-run-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    }
}
