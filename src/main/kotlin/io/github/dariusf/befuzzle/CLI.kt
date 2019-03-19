package io.github.dariusf.befuzzle

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.mashape.unirest.http.Unirest
import io.swagger.util.Yaml
import org.apache.http.HttpHost
import org.apache.http.impl.client.HttpClientBuilder
import org.slf4j.LoggerFactory
import org.zalando.logbook.DefaultHttpLogWriter
import org.zalando.logbook.DefaultHttpLogWriter.Level.DEBUG
import org.zalando.logbook.Logbook
import org.zalando.logbook.httpclient.LogbookHttpRequestInterceptor
import org.zalando.logbook.httpclient.LogbookHttpResponseInterceptor
import java.io.FileInputStream
import java.io.IOException
import java.util.*

/**
 * The very minimal CLI interface
 */
object CLI {

  private val scanner = Scanner(System.`in`)
  private val ymlMapper = Yaml.mapper()

  init {
    ymlMapper.registerModule(KotlinModule())
  }

  fun execute(config: Config, testCases: List<TestCase>) {

    when (config.mode) {
      is Mode.WritePlan -> {
        val plans = testCases.map { PlanTest(it.method, it.endpoint, 100) }
        ymlMapper.writeValue(System.out, plans)
      }
      is Mode.ReadPlan -> {
        val plans : List<PlanTest> = ymlMapper.readValue(FileInputStream(config.mode.file))
        val tests = plans.map { p ->
          testCases.filter { it.method == p.method && it.endpoint == p.endpoint }[0] to p.examples }
        tests.forEach { (t, n) -> t.execute(n) }
      }
      is Mode.Interactive -> interactive(testCases, config)
    }

    println("\uD83D\uDE07️")

    // TODO back to the top, catch exceptions, etc.
  }

  private fun interactive(testCases: List<TestCase>, config: Config) {
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

    val testCase = testCases[choice]

    // TODO better url type
    val url = String.format("http://%s:%d", config.host, config.port)

    System.out.printf("\nYou are about to send up to %d requests to %s.\n" +
        "Hit Enter to confirm.\n? ",
        examples, url)

    scanner.nextLine()

    testCase.execute(examples)
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
