package com.t0r.kestrelojcodesandbox.config;

import com.t0r.kestrelojcodesandbox.docker.DockerContainer;
import lombok.Getter;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
@ConfigurationProperties(prefix = "docker.pool")
public class DockerPoolConfig {

    // todo 这种模式能优化吗？
    @Getter
    private GenericObjectPoolConfig<DockerContainer> config;

    private int maxTotal = 50;
    private int minIdle = 2;
    private int maxIdle = 2;
    private boolean jmxEnabled = true;
    private String jmxNameBase = "com.t0r.kestrelojcodesandbox:type=DockerContainerPool,name=";
    private String jmxNamePrefix = "docker-pool-1";

    @PostConstruct
    public void init() {
        config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(maxTotal);
        config.setMinIdle(minIdle);
        config.setMaxIdle(maxIdle);
        config.setJmxEnabled(jmxEnabled);
        config.setJmxNameBase(jmxNameBase);
        config.setJmxNamePrefix(jmxNamePrefix);
    }
}
