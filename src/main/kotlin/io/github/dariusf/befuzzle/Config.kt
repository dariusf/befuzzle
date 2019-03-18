package io.github.dariusf.befuzzle

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Options
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL

/**
 * All configuration goes through here
 */
class Config @Throws(Exception::class) constructor(args: Array<String>) {

  val host: String
  val port: Int
  val specLocation: String
  val proxyURL: URL?
  val allowUndeclared: Boolean

  companion object {

    private val LOGGER = LoggerFactory.getLogger(Config::class.java.name)

    private const val NAME = "befuzzle"

    private fun help(options: Options) {
      val formatter = HelpFormatter()
      formatter.printHelp("$NAME [server] [spec]", "", options, "")
      System.exit(0)
    }

    private operator fun get(cmd: CommandLine, name: String): String {
      if (cmd.hasOption(name)) {
        return cmd.getOptionValue(name)
      }
      throw IllegalArgumentException("$name is not a valid argument")
    }
  }

  init {
    val options = Options()
    options.addOption(null, "allow-undeclared", false, "allow undeclared HTTP response codes")
    options.addOption(null, "help", false, "print usage info")
    val parser = DefaultParser()
    val cmd = parser.parse(options, args)
    if (cmd.hasOption("help")) {
      help(options)
    }
    allowUndeclared = cmd.hasOption("allow-undeclared")
    if (cmd.argList.size != 2) {
      help(options)
    }

    val server = cmd.args[0]
    val uri = URI(server)
    host = uri.host
    port = uri.port

    specLocation = cmd.args[1]
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
