package io.github.dariusf.befuzzle

import org.quicktheories.core.Gen
import org.quicktheories.generators.Generate.constant
import java.util.*

object Traversable {

  fun <T> sequence(generators: List<Gen<T>>): Gen<List<T>> {
    return sequence(generators, generators.size - 1)
        .map { i -> i as List<T> }
  }

  /**
   * Note that this would perform effects in reverse order, if Gen had effects.
   * Doesn't really matter here.
   */
  private fun <T> sequence(generators: List<Gen<T>>, index: Int): Gen<MutableList<T>> {
    return if (index < 0) {
      constant(ArrayList())
    } else {
      val gen = generators[index]
      gen.flatMap { g ->
        val rec = sequence(generators, index - 1)
        rec.flatMap { r ->
          r.add(g)
          constant(r)
        }
      }
    }
  }
}
