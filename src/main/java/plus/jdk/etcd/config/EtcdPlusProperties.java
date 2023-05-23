package plus.jdk.etcd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * <a href="https://github.com/etcd-io/jetcd/blob/main/docs/SslConfig.md">...</a>
 */
@Data
@ConfigurationProperties(prefix = "plus.jdk.etcd")
public class EtcdPlusProperties {

    /**
     * 是否启动
     */
    private Boolean enabled = false;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 密码
     */
    private String password;

    /**
     * etcd节点列表
     */
    private String[] endpoints;

    /**
     * watcher核心线程数
     */
    private int watcherCoreThreadPollSize = 10;
}
