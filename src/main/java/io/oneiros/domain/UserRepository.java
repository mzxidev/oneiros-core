package io.oneiros.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.oneiros.client.OneirosClient;
import io.oneiros.config.OneirosProperties;
import io.oneiros.core.SimpleOneirosRepository;
import io.oneiros.security.CryptoService;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository extends SimpleOneirosRepository<User, String> {
    public UserRepository(OneirosClient client, ObjectMapper mapper, CryptoService crypto, OneirosProperties props) {
        super(client, mapper, crypto, props);
    }
}