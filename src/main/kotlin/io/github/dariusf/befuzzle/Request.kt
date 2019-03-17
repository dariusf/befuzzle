package io.github.dariusf.befuzzle

import com.fasterxml.jackson.databind.JsonNode
import com.mashape.unirest.http.Unirest
import com.mashape.unirest.http.exceptions.UnirestException
import com.mashape.unirest.request.HttpRequest
import com.mashape.unirest.request.HttpRequestWithBody
import io.swagger.models.HttpMethod
import org.apache.commons.lang3.NotImplementedException
import java.net.URLEncoder.encode

/**
 * A fully-generated HTTP request.
 */
class Request(private val url: String,
              private val method: HttpMethod,
              private val body: JsonNode?,
              private val query: Map<String, String>,
              private val path: Map<String, String>,
              private val header: Map<String, String>,
              private val form: Map<String, String>) {

  @Transient private var request: HttpRequest? = null

  constructor(url: String,
              method: HttpMethod,
              query: Map<String, String>,
              path: Map<String, String>,
              header: Map<String, String>,
              form: Map<String, String>) : this(url, method, null, query, path, header, form)

  fun check(config: Config, declaredResponses: Set<Int>): Boolean {
    println(this)
    try {
      return checkRequest(buildRequest(), config, declaredResponses)
    } catch (e: UnirestException) {
      throw RuntimeException(e)
    }
  }

  private fun buildRequest(): HttpRequest {
    val request1 = this.request
    if (request1 != null) {
      return request1
    }
    val request =
        when (method) {
          HttpMethod.GET -> {
            // TODO change the header to application/x-www-form-urlencoded
            val req = Unirest.get(url)
                .header("Content-Type", "application/json")
            if (body != null) {
              throw RuntimeException("error in specification; GET requests cannot have a body")
            }
            if (!form.isEmpty()) {
              throw RuntimeException("error in specification; GET requests cannot have form parameters")
            }
            query.forEach { name, value -> req.queryString(name, value) }
            path.forEach { k, v -> req.routeParam(k, v) }
            header.forEach { k, v -> req.header(k, v) }
            req
          }
          HttpMethod.POST -> {
            val req = Unirest.post(url)
                .header("Content-Type", "application/json")
            addParams(req)
            req
          }
          HttpMethod.PUT -> {
            val req = Unirest.put(url)
                .header("Content-Type", "application/json")
            addParams(req)
            req
          }
          HttpMethod.PATCH -> {
            val req = Unirest.patch(url)
                .header("Content-Type", "application/json")
            addParams(req)
            req
          }
          HttpMethod.DELETE -> {
            val req = Unirest.delete(url)
                .header("Content-Type", "application/json")
            addParams(req)
            req
          }
          else ->
            // TODO head and options
            throw NotImplementedException(method.toString())
        }
    this.request = request
    return request
  }

  private fun addParams(req: HttpRequestWithBody) {
    if (body != null && !form.isEmpty()) {
      throw IllegalStateException("cannot have both form parameters and body")
    }
    if (body != null) {
      req.body(body)
    }
    query.forEach { name, value -> req.queryString(name, value) }
    path.forEach { k, v -> req.routeParam(k, v) }
    header.forEach { k, v -> req.header(k, v) }
    form.forEach { name, value -> req.field(name, value) }
  }

  /**
   * Returns true if everything is okay.
   */
  @Throws(UnirestException::class)
  private fun checkRequest(req: HttpRequest, config: Config, declaredResponses: Set<Int>): Boolean {
    val response = req.asString()
    val status = response.status

    if (is500(status)) {
      return false
    }
    return is200(status) || config.allowUndeclared || status in declaredResponses
  }

  override fun toString(): String {
    return prettyPrintCurl()
    //    return "Request{" +
    //      "url='" + url + '\'' +
    //      ", method=" + method +
    //      ", body=" + body +
    //      ", query=" + query +
    //      ", path=" + path +
    //      ", header=" + header +
    //      ", form=" + form +
    //      ", request=" + request +
    //      '}';
  }

  /**
   * Displays a HTTP request in curl syntax.
   */
  private fun prettyPrintCurl(): String {
    val sb = StringBuilder()

    var instantiatedUrl = url
    for ((key, value) in path) {
      instantiatedUrl = instantiatedUrl.replace("\\{$key}".toRegex(), value.toString())
    }

    sb.append("curl -v ")
        .append("-X ").append(method).append(' ')

    sb.append(instantiatedUrl).append(' ')

    if (!query.isEmpty()) {
      sb.append('?').append(
          query.entries.joinToString(separator = "&") { e ->
            sb.append(e.key)
                .append('=')
                .append(encode(e.value.toString(), "UTF-8"))
          })
    }

    header.forEach { k, v ->
      sb.append("-H ")
          .append(k)
          .append(' ')
          .append(v)
          .append(' ')
    }

    if (body != null) {
      sb.append("-d '")
          .append(body.toString())
          .append("'")
    } else if (!form.isEmpty()) {
      sb.append("-d '")
          .append(form.entries.joinToString(separator = "&") { e ->
            e.key + "=" + encode(e.value, "UTF-8")
          })
          .append("'")
    }

    return sb.toString()
  }

  companion object {


    private fun is200(status: Int): Boolean {
      return status in 200..299
    }

    private fun is500(status: Int): Boolean {
      return status in 500..599
    }
  }
}
