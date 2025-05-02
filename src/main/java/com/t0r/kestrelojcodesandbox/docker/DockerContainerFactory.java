package com.t0r.kestrelojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import lombok.AllArgsConstructor;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 *  Docker 容器工厂
 */
@AllArgsConstructor
public class DockerContainerFactory implements PooledObjectFactory<DockerContainer> {

    private DockerClient dockerClient;

    private String image;

    /**
     * 创建对象
     * @return
     * @throws Exception
     */
    @Override
    public PooledObject<DockerContainer> makeObject() throws Exception {
        return new DefaultPooledObject<>(DockerContainer.createAndStart(dockerClient, image));
    }

    /**
     * 钝化对象，使其可以被安全重用
     * @param pooledObject
     * @throws Exception
     */
    @Override
    public void passivateObject(PooledObject<DockerContainer> pooledObject) throws Exception {
        // 清理临时文件
        DockerContainer.clear(pooledObject.getObject());
    }

    /**
     * 验证对象是否有效
     * @param pooledObject
     * @return
     */
    @Override
    public boolean validateObject(PooledObject<DockerContainer> pooledObject) {
        // 检查容器是否存活，健康状态
        return true;
    }

    /**
     * 激活对象，使其可以被正常使用
     * @param pooledObject
     * @throws Exception
     */
    @Override
    public void activateObject(PooledObject<DockerContainer> pooledObject) throws Exception {

    }

    /**
     * 销毁对象
     * @param pooledObject
     * @throws Exception
     */
    @Override
    public void destroyObject(PooledObject<DockerContainer> pooledObject) throws Exception {
         DockerContainer.destroy(dockerClient, pooledObject.getObject());
    }
}
