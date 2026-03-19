package com.glassthought.shepherd.core.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A suspend-friendly, Mutex-backed concurrent map.
 *
 * Each individual operation is atomic. If you need atomicity across multiple
 * operations (e.g. clear-then-putAll), you must coordinate externally.
 */
class MutableSynchronizedMap<K, V> {
  private val mutex = Mutex()
  private val backing = mutableMapOf<K, V>()

  /** Returns the value associated with the key, or null. */
  suspend fun get(key: K): V? =
    mutex.withLock {
      backing[key]
    }

  /** Adds or updates an entry. Returns the previous value associated with the key, or null. */
  suspend fun put(key: K, value: V): V? =
    mutex.withLock {
      backing.put(key, value)
    }

  /** Removes the entry for the given key. Returns the removed value, or null. */
  suspend fun remove(key: K): V? =
    mutex.withLock {
      backing.remove(key)
    }

  /**
   * Removes all entries matching the predicate. Returns a list of the removed values.
   */
  suspend fun removeAll(predicate: (K, V) -> Boolean): List<V> =
    mutex.withLock {
      val toRemove = backing.entries.filter { predicate(it.key, it.value) }
      val removedValues = toRemove.map { it.value }
      toRemove.forEach { backing.remove(it.key) }
      removedValues
    }

  /** Returns a snapshot copy of all values. */
  suspend fun values(): List<V> =
    mutex.withLock {
      backing.values.toList()
    }

  /** Returns the number of entries in the map. */
  suspend fun size(): Int =
    mutex.withLock {
      backing.size
    }
}
