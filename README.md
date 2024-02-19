
### 一、如何引入

```xml
<dependency>
    <groupId>plus.jdk</groupId>
    <artifactId>spring-boot-starter-etcd</artifactId>
    <version>1.0.5</version>
</dependency>
```

### 二、etcd的引用配置

```bash
plus.jdk.etcd.enabled=true
plus.jdk.etcd.user-name=admin
plus.jdk.etcd.password=123456
plus.jdk.etcd.endpoints=http://ip1:2379,http://ip2:2379,http://ip3:2379
```

> java 使用etcd不建议使用证书,因为`SslContext Builder`只支持PEM格式的[PKCS#8](https://github.com/etcd-io/jetcd/blob/main/docs/SslConfig.md)私钥文件。

### 三、在项目中引用

下面给出一个使用示例。

```java
import lombok.extern.slf4j.Slf4j;
import plus.jdk.etcd.annotation.EtcdNode;
import plus.jdk.etcd.global.EtcdClient;
import plus.jdk.scheduled.annotation.Scheduled;
import plus.jdk.scheduled.global.IScheduled;

import javax.annotation.Resource;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Scheduled(expr = "0/1 * * * * *")
public class LoadAverageScheduledTask extends IScheduled {


    @EtcdNode(path = "/example/system/aim/loadAverage")
    private volatile double maxLoadAverage = 2.0;

    @EtcdNode(path = "/example/system/aim/random/length")
    private volatile int maxRandomLen = 500000;

    @Resource
    private EtcdClient etcdClient;

    private final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(5, 200,
            0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(200));

    @Override
    protected void doInCronJob() {
        String data = etcdClient.getFirstKV("/example", String.class);
    }
}

```