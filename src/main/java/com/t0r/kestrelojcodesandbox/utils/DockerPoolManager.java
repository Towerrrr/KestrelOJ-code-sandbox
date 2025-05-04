package com.t0r.kestrelojcodesandbox.utils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import com.t0r.kestrelojcodesandbox.config.DockerPoolConfig;
import com.t0r.kestrelojcodesandbox.docker.DockerContainer;
import com.t0r.kestrelojcodesandbox.docker.DockerContainerFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Data
@Slf4j
@Component
public class DockerPoolManager {

    private final DockerClient dockerClient = DockerClientBuilder.getInstance().build();

    private GenericObjectPool<DockerContainer> pool;

    @Resource
    private DockerPoolConfig config;

    @PostConstruct
    public void init() {
        // todo 目前写死
        String image = "openjdk:8-alpine";
        pool = new GenericObjectPool<>(new DockerContainerFactory(dockerClient, image), config.getConfig());
        try {
            pool.addObjects(config.getConfig().getMinIdle());
        } catch (Exception e) {
            log.error("预热容器池失败", e);
            throw new RuntimeException(e);
        }
    }

    public DockerContainer borrowObject() throws Exception {
        return pool.borrowObject();
    }

    public void returnObject(DockerContainer dockerContainer) {
        pool.returnObject(dockerContainer);
    }

    public void invalidateObject(DockerContainer dockerContainer) throws Exception {
        pool.invalidateObject(dockerContainer);
    }

    // todo 当 pool.getNumWaiters() > 5 使触发扩容
}
