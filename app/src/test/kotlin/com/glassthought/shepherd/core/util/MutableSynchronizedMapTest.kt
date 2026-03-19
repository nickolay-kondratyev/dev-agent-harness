package com.glassthought.shepherd.core.util

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class MutableSynchronizedMapTest : AsgardDescribeSpec({

  describe("GIVEN empty map") {
    val map = MutableSynchronizedMap<String, Int>()

    describe("WHEN get(key)") {
      it("THEN returns null") {
        map.get("missing") shouldBe null
      }
    }

    describe("WHEN size()") {
      it("THEN returns 0") {
        map.size() shouldBe 0
      }
    }
  }

  describe("GIVEN map with entry") {
    describe("WHEN get(key)") {
      it("THEN returns value") {
        val map = MutableSynchronizedMap<String, Int>()
        map.put("a", 1)

        map.get("a") shouldBe 1
      }
    }
  }

  describe("GIVEN map WHEN put") {
    describe("AND key is new") {
      it("THEN returns null (no previous value)") {
        val map = MutableSynchronizedMap<String, Int>()

        val previous = map.put("a", 1)

        previous shouldBe null
      }
    }

    describe("AND key already exists") {
      it("THEN returns previous value") {
        val map = MutableSynchronizedMap<String, Int>()
        map.put("a", 1)

        val previous = map.put("a", 2)

        previous shouldBe 1
      }

      it("THEN overwrites with new value") {
        val map = MutableSynchronizedMap<String, Int>()
        map.put("a", 1)
        map.put("a", 2)

        map.get("a") shouldBe 2
      }
    }
  }

  describe("GIVEN map WHEN remove") {
    describe("AND key exists") {
      it("THEN returns removed value") {
        val map = MutableSynchronizedMap<String, Int>()
        map.put("a", 1)

        val removed = map.remove("a")

        removed shouldBe 1
      }

      it("THEN entry is no longer present") {
        val map = MutableSynchronizedMap<String, Int>()
        map.put("a", 1)
        map.remove("a")

        map.get("a") shouldBe null
      }
    }

    describe("AND key does not exist") {
      it("THEN returns null") {
        val map = MutableSynchronizedMap<String, Int>()

        map.remove("missing") shouldBe null
      }
    }
  }

  describe("GIVEN map with entries WHEN removeAll") {
    describe("AND predicate matches some entries") {
      it("THEN returns matched values") {
        val map = MutableSynchronizedMap<String, Int>()
        map.put("a", 1)
        map.put("b", 2)
        map.put("c", 3)

        val removed = map.removeAll { _, v -> v > 1 }

        removed shouldContainExactlyInAnyOrder listOf(2, 3)
      }

      it("THEN matched entries are removed from map") {
        val map = MutableSynchronizedMap<String, Int>()
        map.put("a", 1)
        map.put("b", 2)
        map.put("c", 3)
        map.removeAll { _, v -> v > 1 }

        map.size() shouldBe 1
        map.get("a") shouldBe 1
      }
    }

    describe("AND predicate matches no entries") {
      it("THEN returns empty list") {
        val map = MutableSynchronizedMap<String, Int>()
        map.put("a", 1)

        val removed = map.removeAll { _, v -> v > 100 }

        removed.shouldBeEmpty()
      }

      it("THEN map is unchanged") {
        val map = MutableSynchronizedMap<String, Int>()
        map.put("a", 1)
        map.removeAll { _, v -> v > 100 }

        map.size() shouldBe 1
      }
    }
  }

  describe("GIVEN map with entries WHEN values()") {
    it("THEN returns snapshot of all values") {
      val map = MutableSynchronizedMap<String, Int>()
      map.put("a", 1)
      map.put("b", 2)

      val values = map.values()

      values shouldContainExactlyInAnyOrder listOf(1, 2)
    }

    it("THEN returned list is a snapshot (not affected by subsequent mutations)") {
      val map = MutableSynchronizedMap<String, Int>()
      map.put("a", 1)
      map.put("b", 2)

      val snapshot = map.values()
      map.put("c", 3)

      snapshot shouldHaveSize 2
    }
  }

  describe("GIVEN map WHEN size()") {
    it("THEN returns current entry count") {
      val map = MutableSynchronizedMap<String, Int>()
      map.put("a", 1)
      map.put("b", 2)
      map.put("c", 3)

      map.size() shouldBe 3
    }
  }

  describe("GIVEN concurrent access") {
    it("THEN map is not corrupted by concurrent put and remove operations") {
      runTest {
        val map = MutableSynchronizedMap<Int, Int>()
        val coroutineCount = 100

        // Launch coroutines that put values
        val putJobs = (0 until coroutineCount).map { i ->
          launch {
            map.put(i, i * 10)
          }
        }
        putJobs.forEach { it.join() }

        map.size() shouldBe coroutineCount

        // Launch coroutines that remove even keys
        val removeJobs = (0 until coroutineCount).filter { it % 2 == 0 }.map { i ->
          launch {
            map.remove(i)
          }
        }
        removeJobs.forEach { it.join() }

        map.size() shouldBe coroutineCount / 2

        // Verify only odd keys remain
        val values = map.values()
        values shouldContainExactlyInAnyOrder (0 until coroutineCount).filter { it % 2 != 0 }.map { it * 10 }
      }
    }
  }
})
