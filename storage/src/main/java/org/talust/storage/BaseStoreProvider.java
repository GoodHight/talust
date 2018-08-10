package org.talust.storage;

import lombok.extern.slf4j.Slf4j;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.util.SizeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talust.common.crypto.Utils;
import org.talust.common.tools.Configure;

import java.io.File;
import java.io.IOException;

/**
 * @author Axe-Liu
 * @date 2018/8/6.
 */
@Slf4j
public abstract class BaseStoreProvider implements StoreProvider {

    protected RocksDB db;
    final Options options = new Options()
            .setCreateIfMissing(true)
            .setWriteBufferSize(8 * SizeUnit.KB)
            .setMaxWriteBufferNumber(3)
            .setMaxBackgroundCompactions(10);

    static {
        try {
            RocksDB.loadLibrary();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public BaseStoreProvider(String dir) {
        try {
            String dataBlock = dir;
            File file = new File(dataBlock);
            if (!file.exists()) {
                file.mkdirs();
            }
            log.info("保存交易数据路径为:{}", dataBlock);
            db = RocksDB.open(options, dataBlock);
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
    }

    public void put(byte[] key, byte[] value) throws RocksDBException {
        db.put(key, value);
    }

    public byte[] getBytes(byte[] key) throws RocksDBException {
        return db.get(key);
    }

    @Override
    public void delete(byte[] key) throws RocksDBException {
        db.delete(key);
    }

    /**
     * 释放资源
     *
     * @throws IOException
     */
    public void close() throws IOException {
        db.close();
    }
}
