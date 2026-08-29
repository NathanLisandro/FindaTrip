package com.smarttravel.analyzer.infrastructure.adapter.image;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageProxyPolicyTest {

    @Test void allowsTheImageHostsOfTheSourcesWeScrape() {
        assertThat(ImageProxyPolicy.allows("https://cf.bstatic.com/xdata/images/hotel/square240/1.webp")).isTrue();
        assertThat(ImageProxyPolicy.allows("https://a0.muscache.com/im/pictures/hosting/x.jpeg?im_w=720")).isTrue();
    }

    @Test void refusesAnythingElseSoItCannotBecomeAnOpenProxy() {
        assertThat(ImageProxyPolicy.allows("http://169.254.169.254/latest/meta-data/")).isFalse();
        assertThat(ImageProxyPolicy.allows("http://localhost:8080/api/search")).isFalse();
        assertThat(ImageProxyPolicy.allows("https://exemplo.com/foto.jpg")).isFalse();
    }

    @Test void refusesLookalikeHostsThatMerelyEndWithAnAllowedName() {
        assertThat(ImageProxyPolicy.allows("https://cf.bstatic.com.malicioso.net/x.jpg")).isFalse();
        assertThat(ImageProxyPolicy.allows("https://naomuscache.com/x.jpg")).isFalse();
    }

    @Test void refusesPlainHttpAndMalformedUrls() {
        assertThat(ImageProxyPolicy.allows("http://cf.bstatic.com/x.jpg")).isFalse();
        assertThat(ImageProxyPolicy.allows("nao e uma url")).isFalse();
        assertThat(ImageProxyPolicy.allows(null)).isFalse();
    }
}
