package com.cershy.linyuserver.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OAuth2Config {

    @Value("${oauth2.github.client-id}")
    private String githubClientId;

    @Value("${oauth2.github.client-secret}")
    private String githubClientSecret;

    @Value("${oauth2.github.redirect-uri}")
    private String githubRedirectUri;

    @Bean
    public OAuth2Properties githubOAuth2Properties() {
        return new OAuth2Properties(
                githubClientId,
                githubClientSecret,
                githubRedirectUri,
                "https://github.com/login/oauth/authorize",
                "https://github.com/login/oauth/access_token",
                "https://api.github.com/user"
        );
    }

    @Getter
    public static class OAuth2Properties {
        private final String clientId;
        private final String clientSecret;
        private final String redirectUri;
        private final String authorizeUrl;
        private final String tokenUrl;
        private final String userInfoUrl;

        public OAuth2Properties(String clientId, String clientSecret, String redirectUri,
                                String authorizeUrl, String tokenUrl, String userInfoUrl) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.redirectUri = redirectUri;
            this.authorizeUrl = authorizeUrl;
            this.tokenUrl = tokenUrl;
            this.userInfoUrl = userInfoUrl;
        }

    }
}
