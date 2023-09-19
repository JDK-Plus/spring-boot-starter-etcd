package plus.jdk.etcd.global;

import io.etcd.jetcd.*;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.kv.TxnResponse;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.lease.LeaseRevokeResponse;
import io.etcd.jetcd.lock.LockResponse;
import io.etcd.jetcd.lock.UnlockResponse;
import io.etcd.jetcd.op.Cmp;
import io.etcd.jetcd.op.CmpTarget;
import io.etcd.jetcd.op.Op;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.shaded.com.google.common.base.Charsets;
import io.etcd.jetcd.shaded.com.google.common.base.Function;
import io.etcd.jetcd.watch.WatchEvent;
import lombok.extern.slf4j.Slf4j;
import plus.jdk.etcd.common.IEventProcessor;
import plus.jdk.etcd.config.EtcdPlusProperties;
import plus.jdk.etcd.common.IConfigAdaptor;
import plus.jdk.etcd.model.KeyValuePair;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class EtcdClient {

    private final IConfigAdaptor configAdaptor;

    private final EtcdPlusProperties properties;

    private final Client client;

    public EtcdClient(IConfigAdaptor configAdaptor, EtcdPlusProperties properties) {
        this.properties = properties;
        this.configAdaptor = configAdaptor;
        this.client = Client.builder()
                .endpoints(properties.getEndpoints())
                .user(ByteSequence.from(properties.getUserName().getBytes()))
                .password(ByteSequence.from(properties.getPassword().getBytes()))
                .build();
    }

    public <T> Watch.Watcher watch(String key, IEventProcessor<T> processor, Class<T> clazz) {
        return this.watch(key, processor, clazz, WatchOption.DEFAULT);
    }

    public <T> Watch.Watcher watch(String key, IEventProcessor<T> processor, Class<T> clazz, WatchOption watchOption) {
        ByteSequence watchKey = ByteSequence.from(key, Charsets.UTF_8);
        return client.getWatchClient().watch(watchKey, watchOption, response -> {
            for (WatchEvent event : response.getEvents()) {
                KeyValue keyValue = event.getKeyValue();
                String valueStr = event.getKeyValue().getValue().toString(StandardCharsets.UTF_8);
                T data = configAdaptor.deserialize(valueStr, clazz);
                KeyValuePair<T> keyValuePair = new KeyValuePair<T>(key, data, keyValue);
                try {
                    processor.process(key, event, keyValuePair, watchOption, response);
                } catch (Exception | Error e) {
                    log.error("event process failed, event:{}, keyValues:{}", event, keyValuePair);
                }
            }
        });
    }

    public <T> CompletableFuture<TxnResponse> put(String key, T value, Long expire) throws ExecutionException, InterruptedException {
        PutOption.Builder optionBuilder = PutOption.newBuilder();
        long leaseId = leaseGrant(expire).get();
        optionBuilder.withLeaseId(leaseId);
        KV kvClient = client.getKVClient();
        ByteSequence keyBytes = ByteSequence.from(key.getBytes());
        ByteSequence valueBytes = ByteSequence.from(configAdaptor.serialize(value).getBytes());
        Cmp cmp = new Cmp(keyBytes, Cmp.Op.GREATER, CmpTarget.version(0));
        return kvClient.txn()
                .Then(Op.put(keyBytes, valueBytes, optionBuilder.build()))
                .commit();
    }

    public <T> CompletableFuture<TxnResponse> put(String key, T value, PutOption putOption) {
        KV kvClient = client.getKVClient();
        ByteSequence keyBytes = ByteSequence.from(key.getBytes());
        ByteSequence valueBytes = ByteSequence.from(configAdaptor.serialize(value).getBytes());
        Cmp cmp = new Cmp(keyBytes, Cmp.Op.GREATER, CmpTarget.version(0));
        return kvClient.txn()
                .Then(Op.put(keyBytes, valueBytes, putOption))
                .commit();
    }

    public <T> CompletableFuture<DeleteResponse> delete(String key) {
        KV kvClient = client.getKVClient();
        ByteSequence keyBytes = ByteSequence.from(key.getBytes());
        return kvClient.delete(keyBytes);
    }

    public <T> CompletableFuture<KeyValuePair<T>> getFirstKV(String key, Class<T> clazz) {
        GetOption getOption = GetOption.newBuilder()
                .withSortField(GetOption.SortTarget.VERSION)
                .withSortOrder(GetOption.SortOrder.DESCEND)
                .withLimit(1)
                .build();
        return this.get(key, clazz, getOption).thenApply((Function<List<KeyValuePair<T>>, KeyValuePair<T>>) keyValueDescs -> {
            if (keyValueDescs == null || keyValueDescs.isEmpty()) {
                return null;
            }
            return keyValueDescs.get(0);
        });
    }

    public <T> CompletableFuture<List<KeyValuePair<T>>> scanByPrefix(String prefix, Class<T> clazz) {
        GetOption getOption = GetOption.newBuilder()
                .isPrefix(true)
                .build();
        return this.get(prefix, clazz, getOption);
    }

    public <T> CompletableFuture<List<KeyValuePair<T>>> get(String key, Class<T> clazz, GetOption getOption) {
        KV kvClient = client.getKVClient();
        ByteSequence keyBytes = ByteSequence.from(key.getBytes());
        return kvClient.get(keyBytes, getOption).thenApply((Function<GetResponse, List<KeyValuePair<T>>>) getResponse -> {
            List<KeyValuePair<T>> keyValues = new ArrayList<>();
            if (getResponse == null) {
                return keyValues;
            }
            List<KeyValue> keyValueList = getResponse.getKvs();
            if (keyValueList == null) {
                return keyValues;
            }
            for (KeyValue keyValue : keyValueList) {
                String keyStr = keyValue.getKey().toString(StandardCharsets.UTF_8);
                String valueStr = keyValue.getValue().toString(StandardCharsets.UTF_8);
                T value = null;
                try {
                    value = configAdaptor.deserialize(valueStr, clazz);
                } catch (Exception e) {
                    log.error("deserialize data failed, key:{}, value:{}, clazz:{}", keyStr, valueStr, clazz.getName());
                }
                keyValues.add(new KeyValuePair<>(keyStr, value, keyValue));
            }
            return keyValues;
        });
    }

    public CompletableFuture<Long> leaseGrant(Long ttl, Long timeout, TimeUnit timeUnit) {
        Lease lease = client.getLeaseClient();
        return lease.grant(ttl, timeout, timeUnit).thenApply((Function<LeaseGrantResponse, Long>) leaseGrantResponse -> {
            return leaseGrantResponse.getID();
        });
    }

    public CompletableFuture<Long> leaseGrant(Long ttl) {
        Lease lease = client.getLeaseClient();
        return lease.grant(ttl).thenApply((Function<LeaseGrantResponse, Long>) leaseGrantResponse -> {
            return leaseGrantResponse.getID();
        });
    }

    public CompletableFuture<Long> leaseKeepAlive(Long ttl, Long timeout, TimeUnit timeUnit) {
        Lease lease = client.getLeaseClient();
        return lease.keepAliveOnce(ttl).thenApply(new Function<LeaseKeepAliveResponse, Long>() {
            @Override
            public Long apply(LeaseKeepAliveResponse leaseKeepAliveResponse) {
                return leaseKeepAliveResponse.getID();
            }
        });
    }

    public CompletableFuture<Boolean> leaseRevoke(Long leaseId) {
        Lease lease = client.getLeaseClient();
        return lease.revoke(leaseId).thenApply(new Function<LeaseRevokeResponse, Boolean>() {
            @Override
            public Boolean apply(LeaseRevokeResponse leaseRevokeResponse) {
                return leaseRevokeResponse.getHeader() != null;
            }
        });
    }

    public CompletableFuture<Boolean> lock(String path, Long leaseId) {
        Lock lock = client.getLockClient();
        return lock.lock(ByteSequence.from(path.getBytes()), leaseId).thenApply(new Function<LockResponse, Boolean>() {
            @Override
            public Boolean apply(LockResponse lockResponse) {
                return lockResponse.getHeader() != null;
            }
        });
    }

    public CompletableFuture<Boolean> unlock(String path, Long leaseId) {
        Lock lock = client.getLockClient();
        return lock.unlock(ByteSequence.from(path.getBytes())).thenApply(new Function<UnlockResponse, Boolean>() {
            @Override
            public Boolean apply(UnlockResponse unlockResponse) {
                return unlockResponse.getHeader() != null;
            }
        });
    }
}
