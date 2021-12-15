package reactive.kafka;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.support.ErrorMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

@SpringBootApplication
@Slf4j
public class ReactiveProcessorApplication {

	private final Log logger = LogFactory.getLog(getClass());

	private AtomicBoolean semaphore = new AtomicBoolean(true);

	public static void main(String[] args) {
		SpringApplication.run(ReactiveProcessorApplication.class, args);
	}

  @Bean
  public Function<Flux<String>, Flux<String>> aggregate() {
    return inbound ->
        inbound
            .log()
            .map(
                s -> {
                  if (s.equals("foo")) {
                    log.info("Going to throw");
                    throw new RuntimeException("run time exception manually thrown");
                  }
                  log.info("no Exception");
                  return s;
                })
            .onErrorContinue(
                (err, i) -> {
                  log.info("log for onErrorContinue={}", i);
                });

    // .doOnError(t -> log.error("do on error", t)); //stream breaks

    // .onErrorResume(err -> {log.info("log for onErrorResume={}"); return Mono.just("sssss");});//stream breaks

    // .onErrorContinue((err, i) -> {log.info("log for onErrorContinue={} invoke retry service here", i);});
  }
  
  @Bean
  public Function<Flux<String>, Flux<String>> aggregateWithInner() {
    return inbound ->
        inbound
            .map(
                s -> {
                  inner()
                      .map(
                          x -> {
                            log.info("inner flux map");
                            return Flux.error(new RuntimeException());
                          }).blockFirst();
                  log.info("After inner");
                  return s;
                });
  }



  public Flux<String> inner() {
    log.info("In inner");
    return Flux.just("AAAA");
  }

	// .doOnError(t -> log.error("do on error", t)); //stream breaks

	//.onErrorResume(err -> {log.info("log for onErrorResume={}"); return Mono.just("sssss");}); //stream breaks

	//.onErrorContinue((err, i) -> {log.info("log for onErrorContinue={} invoke retry service here", i);});


	// .doOnError(t -> log.error("do on error", t)); //stream breaks

	//.onErrorResume(err -> {log.info("log for onErrorResume={}"); return Mono.just("sssss");}); //stream breaks

	//.onErrorContinue((err, i) -> {log.info("log for onErrorContinue={} invoke retry service here", i);});


	//Following source and sinks are used for testing only.
	//Test source will send data to the same destination where the processor receives data
	//Test sink will consume data from the same destination where the processor produces data

	@Bean
	public Supplier<String> testSource() {
		return () -> this.semaphore.getAndSet(!this.semaphore.get()) ? "foo" : "bar";

	}

	@Bean
	public Consumer<String>  testSink() {
		return payload -> logger.info("Data received: " + payload);

	}
/*
	@ServiceActivator(inputChannel = "transform.errors")
	public void handlePubError(final ErrorMessage em) {
		log.error("publisher error" + em.toString());
	}*/

	@ServiceActivator(inputChannel = "errorChannel")
	public void handle(final ErrorMessage em) {
		log.error("Error caught" + em.toString());
	}

	@PostConstruct
	protected void setHooks() {
		Hooks.onErrorDropped(t -> {
			log.error("invoked hooks on errordropped ", t);
		});
	}

	@PreDestroy
	protected void cleanupHooks() {
		Hooks.resetOnErrorDropped();
	}
}
