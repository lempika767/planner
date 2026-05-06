package hometask.planner.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@Tag(name = "Hello", description = "Hello endpoints")
public class HelloController {

    @GetMapping("/hello")
    @Operation(summary = "Returns a hello message")
    public Mono<String> hello() {
        return Mono.just("Hello from WebFlux!");
    }

    @GetMapping("/items")
    @Operation(summary = "Returns a list of items")
    public Flux<String> items() {
        return Flux.just("item1", "item2", "item3");
    }
}
