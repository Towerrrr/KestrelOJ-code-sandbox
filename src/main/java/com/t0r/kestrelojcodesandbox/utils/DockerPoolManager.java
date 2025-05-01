package com.t0r.kestrelojcodesandbox.utils;

import com.github.dockerjava.api.DockerClient;
import com.t0r.kestrelojcodesandbox.config.DockerPoolConfig;
import com.t0r.kestrelojcodesandbox.docker.DockerContainer;
import com.t0r.kestrelojcodesandbox.docker.DockerContainerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class DockerPoolManager {

    private GenericObjectPool<DockerContainer> pool;

    private DockerClient dockerClient;

    private String image;

    @PostConstruct
    public void init(DockerPoolConfig config) {
        pool = new GenericObjectPool<>(new DockerContainerFactory(dockerClient, image), config);
        try {
            pool.addObjects(config.getMinIdle());
        } catch (Exception e) {
            log.error("预热容器池失败", e);
            throw new RuntimeException(e);
        }
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
}
