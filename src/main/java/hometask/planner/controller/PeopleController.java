package hometask.planner.controller;

import hometask.planner.entity.Person;
import hometask.planner.service.PeopleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/people")
@RequiredArgsConstructor
public class PeopleController {
    private final PeopleService peopleService;

    // GET /api/people/{name} — fetch or create a person
    @GetMapping("/{name}")
    public Mono<ResponseEntity<Person>> getOrAddPerson(@PathVariable String name) {
        return peopleService.getOrAddPerson(name)
                .map(ResponseEntity::ok);
    }

    // DELETE /api/people/{name} — remove a person
    @DeleteMapping("/{name}")
    public Mono<ResponseEntity<Void>> removePerson(@PathVariable String name) {
        return peopleService.removePerson(name)
                .map(removed -> removed
                        ? ResponseEntity.<Void>noContent().build()
                        : ResponseEntity.<Void>notFound().build()
                );
    }
}