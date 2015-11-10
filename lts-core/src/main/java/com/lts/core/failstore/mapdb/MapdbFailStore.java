package com.lts.core.failstore.mapdb;

import com.lts.core.commons.file.FileUtils;
import com.lts.core.commons.utils.JSONUtils;
import com.lts.core.domain.KVPair;
import com.lts.core.failstore.AbstractFailStore;
import com.lts.core.failstore.FailStoreException;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;

/**
 * see http://www.mapdb.org/
 *
 * @author Robert HG (254963746@qq.com) on 11/10/15.
 */
public class MapdbFailStore extends AbstractFailStore {

    public static final String name = "mapdb";
    private DB db;
    private ConcurrentNavigableMap<String, String> map;

    public MapdbFailStore(File dbPath) {
        super(dbPath, true);
    }

    public MapdbFailStore(File dbPath, boolean needLock) {
        super(dbPath, needLock);
    }

    @Override
    protected void init() {
        String dbName = dbPath.getPath() + "/lts.db";
        db = DBMaker.fileDB(new File(dbName))
                .closeOnJvmShutdown()
                .encryptionEnable("lts")
                .make();
    }

    @Override
    protected String getName() {
        return name;
    }

    @Override
    public void open() throws FailStoreException {
        try {
            map = db.treeMap("lts");
        } catch (Exception e) {
            throw new FailStoreException(e);
        }
    }

    @Override
    public void put(String key, Object value) throws FailStoreException {
        try {
            String valueString = JSONUtils.toJSONString(value);
            map.put(key, valueString);
            // persist changes into disk
            db.commit();
        } catch (Exception e) {
            db.rollback();
            throw new FailStoreException(e);
        }
    }

    @Override
    public void delete(String key) throws FailStoreException {
        try {
            map.remove(key);
            // persist changes into disk
            db.commit();
        } catch (Exception e) {
            db.rollback();
            throw new FailStoreException(e);
        }
    }

    @Override
    public void delete(List<String> keys) throws FailStoreException {
        if (keys == null || keys.size() == 0) {
            return;
        }
        try {
            for (String key : keys) {
                map.remove(key);
            }
            db.commit();
        } catch (Exception e) {
            db.rollback();
            throw new FailStoreException(e);
        }
    }

    @Override
    public <T> List<KVPair<String, T>> fetchTop(int size, Type type) throws FailStoreException {

        List<KVPair<String, T>> list = new ArrayList<KVPair<String, T>>(size);
        if (map.size() == 0) {
            return list;
        }
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            T value = JSONUtils.parse(entry.getValue(), type);
            KVPair<String, T> pair = new KVPair<String, T>(key, value);
            list.add(pair);
            if (list.size() >= size) {
                break;
            }
        }
        return list;
    }

    @Override
    public void close() throws FailStoreException {
        try {
            db.close();
        } catch (Exception e) {
            throw new FailStoreException(e);
        }
    }

    @Override
    public void destroy() throws FailStoreException {
        try {
            close();
        } catch (Exception e) {
            throw new FailStoreException(e);
        } finally {
            FileUtils.delete(dbPath);
        }
    }
}
