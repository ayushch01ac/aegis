package com.aegis.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisConfigTest {

    @Test
    void redisPropertiesDefaultPrefix() {
        RedisProperties properties = new RedisProperties(null);
        assertEquals("aegis:", properties.keyPrefix());

        RedisProperties customProps = new RedisProperties("custom:");
        assertEquals("custom:", customProps.keyPrefix());
    }

    @Test
    void stringRedisTemplateCreation() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConfig config = new RedisConfig();

        StringRedisTemplate template = config.stringRedisTemplate(connectionFactory);

        assertNotNull(template);
        assertEquals(connectionFactory, template.getConnectionFactory());
    }
}
