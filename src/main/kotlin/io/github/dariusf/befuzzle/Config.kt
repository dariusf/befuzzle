package io.github.dariusf.befuzzle

import org.apache.commons.cli.*
import org.slf4j.LoggerFactory
import java.net.URL

/**
 * All configuration goes through this object
 */
class Config @Throws(Exception::class) constructor(args: Array<String>) {

  val host: String
  val port: Int
  val input: String
  val proxyURL: URL?
  val allowUndeclared: Boolean

  companion object {

    private val LOGGER = LoggerFactory.getLogger(Config::class.java.name)

    private val NAME = "befuzzle"

    private fun help(options: Options) {
      val formatter = HelpFormatter()
      formatter.printHelp(NAME + " [spec]", options)
      System.exit(0)
    }

    private operator fun get(cmd: CommandLine, name: String): String {
      if (cmd.hasOption(name)) {
        return cmd.getOptionValue(name)
      }
      throw IllegalArgumentException(name + " is not a valid argument")
    }
  }

  init {
    val options = Options()
    options.addOption("h", "host", true, "the host to hit [default: localhost]")
    options.addOption("p", "port", true, "the port of the host to hit [default: 8080]")
    options.addOption(null, "allow-undeclared", false, "allow undeclared HTTP response codes")
    options.addOption(null, "help", false, "print usage info")
    val parser = DefaultParser()
    val cmd = parser.parse(options, args)
    if (cmd.hasOption("help")) {
      help(options)
    }
    host = cmd.getOptionValue("host", "localhost")
    val portS = cmd.getOptionValue("port", "8080")
    try {
      port = Integer.parseInt(portS)
    } catch (e: Exception) {
      throw ParseException("invalid port " + portS)
    }
    allowUndeclared = cmd.hasOption("allow-undeclared")
    if (cmd.argList.size != 1) {
      help(options)
    }
    input = cmd.args[0]
    val httpProxy = System.getenv("http_proxy")
    if (httpProxy != null) {
      LOGGER.info("Using proxy url {}", httpProxy)
      this.proxyURL = URL(httpProxy)
    } else {
      LOGGER.debug("No proxy url set")
      this.proxyURL = null
    }
  }
}
