package io.oneiros.domain;

import io.oneiros.annotation.OneirosEncrypted;
import io.oneiros.annotation.OneirosEntity;
import io.oneiros.annotation.OneirosID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@OneirosEntity("users")
public class User {
    @OneirosID
    private String id;
    private String username;

    @OneirosEncrypted
    private String password;
}