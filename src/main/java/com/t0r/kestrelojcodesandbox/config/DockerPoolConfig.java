package com.t0r.kestrelojcodesandbox.config;

import com.t0r.kestrelojcodesandbox.docker.DockerContainer;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "docker.pool")
public class DockerPoolConfig extends GenericObjectPoolConfig<DockerContainer> {
    private int maxTotal = 50;
    private int minIdle = 2;
    private int maxIdle = 2;
}
