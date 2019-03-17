package io.github.dariusf.befuzzle

import org.junit.Test
import org.quicktheories.QuickTheory.qt
import org.quicktheories.generators.Generate.constant
import java.util.*

class FuzzTest {

  @Test
  @Throws(Exception::class)
  fun test() {

    val sequence = Traversable.sequence(Arrays.asList(constant<Any>(1), constant<Any>(2), constant<Any>(3)))

    // TODO use this in a test
    qt()
        .forAll(sequence)
        .check { i ->
          println(i)
          i.size > 0
        }
  }
}
