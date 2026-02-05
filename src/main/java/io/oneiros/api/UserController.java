package io.oneiros.api;

import io.oneiros.client.OneirosClient;
import io.oneiros.domain.User;
import io.oneiros.domain.UserRepository;
import io.oneiros.query.OneirosQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final OneirosClient client;

    @GetMapping
    public Flux<User> getAll() {
        return userRepository.findAll();
    }

    @GetMapping("/search")
    public Flux<User> searchByUsername(@RequestParam String username) {
        return OneirosQuery.select(User.class)
                .where("username").like(username)
                .execute(client);
    }

    @GetMapping("/filter")
    public Flux<User> filterByEmail(@RequestParam String email) {
        return OneirosQuery.select(User.class)
                .where("email").is(email)
                .execute(client);
    }

    @GetMapping("/top")
    public Flux<User> getTop(@RequestParam(defaultValue = "10") int limit) {
        return OneirosQuery.select(User.class)
                .orderBy("username")
                .limit(limit)
                .execute(client);
    }

    @PostMapping
    public Mono<User> create(@RequestParam String username, @RequestParam String email) {
        User user = new User(null, username, email);
        return userRepository.save(user);
    }

    @GetMapping("/{id}")
    public Mono<User> getOne(@PathVariable String id) {
        return userRepository.findById(id);
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable String id) {
        return userRepository.deleteById(id);
    }

    @PutMapping("/{id}")
    public Mono<User> update(@PathVariable String id,
                            @RequestParam String username,
                            @RequestParam String email) {
        User user = new User(id, username, email);
        return userRepository.save(user);
    }
}

