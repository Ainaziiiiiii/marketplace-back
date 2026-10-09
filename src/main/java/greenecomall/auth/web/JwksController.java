package greenecomall.auth.web;

import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Публичный ключ для проверки подписи access-JWT — для внешних потребителей токена. */
@RestController
public class JwksController {

    private final JWKSet jwkSet;

    public JwksController(JWKSet jwkSet) {
        this.jwkSet = jwkSet;
    }

    @GetMapping("/oauth2/jwks")
    public Map<String, Object> jwks() {
        return jwkSet.toJSONObject();
    }
}
