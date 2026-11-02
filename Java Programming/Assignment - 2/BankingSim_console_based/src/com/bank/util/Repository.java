package com.bank.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A small generic in-memory store: Repository&lt;Account&gt;, Repository&lt;Customer&gt;, etc.
 * T is the type parameter; it must be Serializable so the repository can be saved to disk.
 */
public class Repository<T extends Serializable> implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, T> items = new HashMap<String, T>();

    public synchronized void add(String id, T item) {
        items.put(id, item);
    }

    /** Returns null if nothing is stored under this id. */
    public synchronized T find(String id) {
        return items.get(id);
    }

    public synchronized boolean exists(String id) {
        return items.containsKey(id);
    }

    public synchronized boolean remove(String id) {
        return items.remove(id) != null;
    }

    /** Returns a copy, so callers can loop safely while others add items. */
    public synchronized Collection<T> findAll() {
        return new ArrayList<T>(items.values());
    }

    public synchronized int count() {
        return items.size();
    }
}
