package br.com.commercehub.auth.adapter.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void hashNeverEqualsRawPassword() {
        String hash = hasher.hash("senha-secreta");

        assertThat(hash).isNotEqualTo("senha-secreta");
    }

    @Test
    void matchesReturnsTrueForCorrectPassword() {
        String hash = hasher.hash("senha-secreta");

        assertThat(hasher.matches("senha-secreta", hash)).isTrue();
    }

    @Test
    void matchesReturnsFalseForWrongPassword() {
        String hash = hasher.hash("senha-secreta");

        assertThat(hasher.matches("senha-errada", hash)).isFalse();
    }

    @Test
    void twoHashesOfSamePasswordAreDifferent() {
        String firstHash = hasher.hash("senha-secreta");
        String secondHash = hasher.hash("senha-secreta");

        assertThat(firstHash).isNotEqualTo(secondHash);
    }
}
