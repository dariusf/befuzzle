package io.github.dariusf.befuzzle

import com.fasterxml.jackson.databind.JsonNode
import io.swagger.models.HttpMethod
import org.apache.http.client.utils.URIBuilder
import org.quicktheories.QuickTheory
import org.quicktheories.core.Gen
import org.quicktheories.generators.Generate.constant

/**
 * An API test case that hasn't been made concrete.
 */
class TestCase(private val host: String,
               val endpoint: String,
               private val port: Int,
               val method: HttpMethod,
               private val body: Gen<JsonNode>?,
               private val query: Gen<Map<String, JsonNode>>,
               private val path: Gen<Map<String, JsonNode>>,
               private val header: Gen<Map<String, JsonNode>>,
               private val form: Gen<Map<String, JsonNode>>) {

  fun execute() {
    QuickTheory.qt()
        .forAll(generator())
        .check({ it.check() })
  }

  private fun generator(): Gen<Request> {
    return header.flatMap { hs ->
      form.flatMap { fs ->
        path.flatMap { ps ->
          query.flatMap<Request> { qs ->

            val url = URIBuilder()
                .setScheme("http")
                .setHost(host)
                .setPort(port)
                .setPath(endpoint)
                .build().toASCIIString()

            if (body == null) {
              constant(Request(url, method, qs, ps, hs, fs))
            } else {
              body.map { bd -> Request(url, method, bd, qs, ps, hs, fs) }
            }
          }
        }
      }
    }
  }
}
