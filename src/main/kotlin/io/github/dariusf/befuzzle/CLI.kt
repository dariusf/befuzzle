package io.github.dariusf.befuzzle

import com.mashape.unirest.http.Unirest
import org.apache.http.HttpHost
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClientBuilder
import org.slf4j.LoggerFactory
import org.zalando.logbook.DefaultHttpLogWriter
import org.zalando.logbook.DefaultHttpLogWriter.Level.DEBUG
import org.zalando.logbook.Logbook
import org.zalando.logbook.httpclient.LogbookHttpRequestInterceptor
import org.zalando.logbook.httpclient.LogbookHttpResponseInterceptor
import java.io.IOException
import java.util.*

/**
 * The very minimal CLI interface
 */
object CLI {

  private val scanner = Scanner(System.`in`)

  fun execute(config: Config, testCases: List<TestCase>) {

    println("\nWhich endpoint would you like to test?\n")

    val choice = choose(testCases.map { "${it.method} ${it.endpoint}" })

    println("\nWith how many examples?")

    val oExamples = readInt()
    val examples =
        if (oExamples == null) {
          println("Defaulting to 100 examples")
          100
        } else {
          oExamples
        }

    System.setProperty("QT_EXAMPLES", Integer.toString(examples))

    val testCase = testCases[choice]

    val url = URIBuilder()
        .setScheme("http")
        .setHost(config.host)
        .setPort(config.port)
        .build().toASCIIString()

    System.out.printf("\nYou are about to send up to %d requests to %s.\n" +
        "Hit Enter to confirm.\n? ",
        examples, url)

    scanner.nextLine()

    testCase.execute()

    println("\uD83D\uDE07️")

    // TODO back to the top, catch exceptions, etc.
  }

  private fun <T> choose(choices: List<T>): Int {
    var i = 1
    for (choice in choices) {
      println("${i++}) $choice")
    }

    val oChoice = readInt()
    if (oChoice == null) {
      println("invalid choice")
      return choose(choices)
    }

    return oChoice - 1
  }

  private fun readInt(): Int? {
    print("? ")
    System.out.flush()

    val line = scanner.nextLine()
    val choice: Int
    try {
      choice = Integer.parseInt(line)
    } catch (e: Exception) {
      return null
    }

    return choice
  }

  @Throws(IOException::class)
  fun shutdown() {
    Unirest.shutdown()
  }

  private val LOGGER = LoggerFactory.getLogger(CLI.javaClass.name)

  fun setup(config: Config) {

    val logbook = Logbook.builder()
        .writer(DefaultHttpLogWriter(LOGGER, DEBUG))
        .build()
    val client = HttpClientBuilder.create()
        .addInterceptorFirst(LogbookHttpRequestInterceptor(logbook))
        .addInterceptorFirst(LogbookHttpResponseInterceptor())
        .build()
    Unirest.setHttpClient(client)

    // Only do this after we set the custom http client

    Unirest.setObjectMapper(Utility.unirestJacksonOM)

    if (config.proxyURL != null) {
      Unirest.setProxy(HttpHost(
          config.proxyURL.host,
          config.proxyURL.port))
    }
  }
}
