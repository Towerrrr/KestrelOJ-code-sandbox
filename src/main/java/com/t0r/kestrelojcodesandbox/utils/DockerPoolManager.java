package com.t0r.kestrelojcodesandbox.utils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import com.t0r.kestrelojcodesandbox.config.DockerPoolConfig;
import com.t0r.kestrelojcodesandbox.docker.DockerContainer;
import com.t0r.kestrelojcodesandbox.docker.DockerContainerFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
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
        pool = new GenericObjectPool<>(new DockerContainerFactory(dockerClient, image), config);
        try {
            pool.addObjects(config.getMinIdle());
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

    /**
     * 暴露池监控指标（活跃数、空闲数）
     * @return
     */
    @ReadOperation
    public Map<String, Integer> metrics() {
        Map<String, Integer> metricsMap = new HashMap<>();
        metricsMap.put("active", pool.getNumActive());
        metricsMap.put("idle", pool.getNumIdle());
        return metricsMap;
    }


    // todo 在utils中增加 DockerPoolMetrics.java 通过JMX暴露池监控指标
    // todo 当 pool.getNumWaiters() > 5 使触发扩容
}
