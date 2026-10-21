package vd.affsn.sentinelle.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ExtracteurBearerTest {

    @ParameterizedTest
    @ValueSource(strings = {"Bearer abc.def.ghi", "bearer abc.def.ghi", "BEARER abc.def.ghi", "BeArEr abc.def.ghi"})
    void schemaInsensibleALaCasse(String valeur) {
        assertThat(ExtracteurBearer.extraire(valeur)).contains("abc.def.ghi");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "Bearer", "Bearer ", "Bearer    ", "Basic dXRpbGlzYXRldXI6bW90LWRlLXBhc3Nl", "Token abc.def.ghi",
        "Bearerabc.def.ghi", "abc.def.ghi", "Bearer abc def", "Bearer  abc.def.ghi", " Bearer abc.def.ghi"
    })
    void formesInvalides(String valeur) {
        assertThat(ExtracteurBearer.extraire(valeur)).isEmpty();
    }

    @Test
    void neModifiePasLeJeton() {
        assertThat(ExtracteurBearer.extraire("Bearer AbC-_.XyZ-_.123-_")).contains("AbC-_.XyZ-_.123-_");
    }
}
