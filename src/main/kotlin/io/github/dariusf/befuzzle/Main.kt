package io.github.dariusf.befuzzle

@Throws(Exception::class)
fun main(args: Array<String>) {

  val config = Config(args)

  CLI.setup(config)

  val testCases = Fuzz.createTestCases(config)

  CLI.execute(config, testCases)

  CLI.shutdown()
}
