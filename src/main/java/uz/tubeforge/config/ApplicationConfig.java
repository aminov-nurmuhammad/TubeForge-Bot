package uz.tubeforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;

@Configuration
public class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean("mediaJobExecutor")
    TaskExecutor mediaJobExecutor(MediaProperties properties) {
        return executor(properties.maxConcurrentJobs(), properties.maxQueuedJobs(), "media-job-");
    }

    @Bean("mediaInspectionExecutor")
    TaskExecutor mediaInspectionExecutor(MediaProperties properties) {
        return executor(properties.maxConcurrentInspections(), properties.maxQueuedInspections(), "media-inspect-");
    }

    private TaskExecutor executor(int size, int queueCapacity, String prefix) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
