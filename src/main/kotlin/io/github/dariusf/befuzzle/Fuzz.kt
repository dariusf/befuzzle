package io.github.dariusf.befuzzle

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import io.github.dariusf.befuzzle.Traversable.sequence
import io.swagger.models.ArrayModel
import io.swagger.models.Model
import io.swagger.models.ModelImpl
import io.swagger.models.RefModel
import io.swagger.models.parameters.*
import io.swagger.models.properties.*
import io.swagger.parser.SwaggerParser
import org.apache.commons.lang3.NotImplementedException
import org.quicktheories.WithQuickTheories
import org.quicktheories.core.Gen
import org.quicktheories.core.RandomnessSource
import org.quicktheories.generators.Generate
import org.quicktheories.generators.Generate.*
import java.util.*

/**
 * All functionality for turning OpenAPI specs into QuickTheories generators
 */
class Fuzz(swaggerDefs: MutableMap<String, Model>) : WithQuickTheories {

  private var definitions: Map<String, Gen<JsonNode>>

  init {
    this.definitions = swaggerDefs.entries
        .map { p -> p.key to modelGen(p.value) }.toMap()
  }

  /**
   * This is a late-binding generator that allows us to support lazy
   * references to definitions, allowing everything to work with recursion
   * and cyclic graphs.
   */
  private inner class DynamicGen internal constructor(
      private val key: String,
      private val definition: String
  ) : Gen<Prop<JsonNode>> {
    override fun generate(`in`: RandomnessSource): Prop<JsonNode> {
      return Prop(key, definitions[definition]!!.generate(`in`))
    }
  }

  /**
   * A named, concrete value that's been fully generated.
   */
  private class Prop<out T>(internal val name: String, internal val value: T)

  private fun propertyGen(name: String, prop: Property): Gen<Prop<JsonNode>> {
    return propertyGen(prop)
        .map { o -> Prop(name, o) }
  }

  /**
   * Every property is nullable...
   */
  private fun propertyGen(prop: Property): Gen<JsonNode> {
    return oneOf(
        constant<JsonNode> { NullNode.instance },
        propertyGenAux(prop))
  }

  private fun propertyGenAux(prop: Property): Gen<JsonNode> {
    when (prop) {
      is LongProperty ->
        return if (prop.enum != null) {
          pick(prop.enum)
        } else {
          longs().all()
        }.map(Jackson::fromObject)
      is IntegerProperty ->
        return if (prop.enum != null) {
          pick(prop.enum)
        } else {
          integers().all()
        }.map(Jackson::fromObject)
      is FloatProperty ->
        return if (prop.enum != null) {
          pick(prop.enum)
        } else {
          floats().any()
        }.map(Jackson::fromObject)
      is DoubleProperty ->
        return if (prop.enum != null) {
          pick(prop.enum)
        } else {
          doubles().any()
        }.map(Jackson::fromObject)
      is StringProperty ->
        // TODO check pattern
        return if (prop.enum != null) {
          pick(prop.enum)
        } else {
          val min = prop.minLength ?: 0
          val max = prop.maxLength ?: 5
          stringGen(min, max)
        }.map(Jackson::fromObject)
      is BooleanProperty ->
        return Generate.booleans().map(Jackson::fromObject)
      is DateTimeProperty ->
        //      return dates().withMilliseconds(new Date().getTime())
        return constant<String>(Utility::nowISO8601).map(Jackson::fromObject)
    //      2017-12-28T16:30:11.853+0000
      is ArrayProperty -> {
        val itemP = prop.items
        return if (itemP is RefProperty) {
          dynamicGen(itemP)
        } else {
          val items = propertyGen(itemP)
          val min = prop.minItems ?: 0
          val max = prop.maxItems ?: 5
          lists().of(items).ofSizeBetween(min, max)
        }.map(Jackson::fromObject)
      }
      is MapProperty -> {
        val value = prop.additionalProperties
        val keys = stringGen(0, 5)
        val values = propertyGen(value)
        return maps().of(keys, values).ofSize(4)
            .map(Jackson::fromObject)
      }
      is ObjectProperty -> {
        // TODO check how object works
        val keys = stringGen(0, 5)
        val values = stringGen(0, 5)
        return maps().of(keys, values).ofSize(4)
            .map(Jackson::fromObject)
      }
      is RefProperty ->
        throw IllegalStateException("this should have been handled before this point")
      else ->
        throw NotImplementedException("unimplemented property type " + prop.javaClass.simpleName)
    }
  }

  private fun stringGen(min: Int, max: Int): Gen<String> {
    return strings().basicLatinAlphabet().ofLengthBetween(min, max)
  }

  fun modelGen(model: Model): Gen<JsonNode> {

    when (model) {
      is ModelImpl -> {

        // May happen for interfaces
        if (model.getProperties() == null) {
          // TODO might want to pull this out
          return constant(Jackson.fromObject(Jackson.MAPPER.createObjectNode()))
        }
        val generators = model.properties.entries.map { (k, v) ->

          if (v is RefProperty) {
            dynamicGen(k, v)
          } else {
            propertyGen(k, v)
          }
        }

        val sequence: Gen<List<Prop<JsonNode>>> = sequence(generators)
        return sequence.map { l -> l.map { k -> k.name to k.value }.toMap() }
            .map(Jackson::fromObject)
      }
      is RefModel -> {
        val defName = model.simpleRef
        return definitions[defName]
            ?: throw IllegalStateException("$defName not defined; problem parsing?")
      }
      is ArrayModel -> {

        val items = model.items
        return if (items is RefProperty) {
          dynamicGen(items).map(Jackson::fromObject)
        } else {
          propertyGen(ArrayProperty(items))
        }

      }
      else -> throw NotImplementedException("need to handle other model types: " + model)
    }
  }

  private fun dynamicGen(p: RefProperty): Gen<JsonNode> {
    return dynamicGen("", p).map { x -> x.value }
  }

  private fun dynamicGen(key: String, p: RefProperty): Gen<Prop<JsonNode>> {
    val name = p.simpleRef
    return DynamicGen(key, name)
  }

  private fun queryParamsGenerator(params: List<Parameter>): Gen<Map<String, JsonNode>> {
    val queryGen: Gen<Map<String, JsonNode>>
    val queryParams = params
        .filter { p -> p is QueryParameter }
        .map { p -> p as QueryParameter }

    if (queryParams.isEmpty()) {
      queryGen = constant(HashMap())
    } else {
      val generators = queryParams
          .map { qp ->

            // TODO handle multiple cases. maybe not be JsonNode...
            // TODO what is collection format = multi?
            val items = qp.items
            if (items == null) {
              when (qp.type) {
                "string" ->
                  propertyGen(qp.name, StringProperty())
                else -> throw NotImplementedException("unimplemented type " + qp.type)
              }
            } else {
              propertyGen(qp.name, items)
            }

          }

      queryGen = sequence(generators).map { l ->
        l.map { p -> p.name to p.value }.toMap()
      }
    }
    return queryGen
  }

  private fun bodyParamsGenerator(params: List<Parameter>): Gen<JsonNode>? {
    val bodyParams = params
        .filter { p -> p is BodyParameter }
        .map { p -> p as BodyParameter }
    return when {
      bodyParams.size > 1 -> throw RuntimeException("more than one body parameter")
      bodyParams.size == 1 -> {
        val body = bodyParams[0]
        val schema = body.schema
        modelGen(schema)
      }
      else -> null
    }
  }

  private fun pathParamsGenerator(params: List<Parameter>): Gen<Map<String, JsonNode>> {
    val pathGen: Gen<Map<String, JsonNode>>
    val pathParams = params
        .filter { p -> p is PathParameter }
        .map { p -> p as PathParameter }

    if (pathParams.isEmpty()) {
      pathGen = constant(HashMap())
    } else {
      val generators = pathParams.map { qp ->

        when (qp.type) {
          "string" ->
            propertyGen(qp.name, StringProperty())
          "integer"
          -> if (qp.format == "int64") {
            propertyGen(qp.name, LongProperty())
          } else {
            propertyGen(qp.name, IntegerProperty())
          }
          else -> throw NotImplementedException("unimplemented type " + qp.type)
        }
      }

      val sequence = sequence(generators)
      pathGen = sequence.map { l ->
        l.map { p -> p.name to p.value }.toMap()
      }
    }
    return pathGen
  }

  private fun formParamsGenerator(params: List<Parameter>): Gen<Map<String, JsonNode>> {
    val formGen: Gen<Map<String, JsonNode>>
    val formParams = params
        .filter { p -> p is FormParameter }
        .map { p -> p as FormParameter }

    if (formParams.isEmpty()) {
      formGen = constant(HashMap())
    } else {
      val generators = formParams.map { qp ->

        when (qp.type) {
          "string" ->
            propertyGen(qp.name, StringProperty())
          "integer"
          -> if (qp.format == "int64") {
            propertyGen(qp.name, LongProperty())
          } else {
            propertyGen(qp.name, IntegerProperty())
          }
          "file" ->
            // TODO bogus implementation; we might have to use a type other than JsonNode to create files
            propertyGen(qp.name, StringProperty())
          else -> throw NotImplementedException("unimplemented type " + qp.type)
        }

      }

      val sequence = sequence(generators)
      formGen = sequence.map { l ->
        l.map { p -> p.name to p.value }.toMap()
      }
    }
    return formGen
  }

  private fun headerParamsGenerator(params: List<Parameter>): Gen<Map<String, JsonNode>> {
    val headerGen: Gen<Map<String, JsonNode>>
    val headerParams = params
        .filter { p -> p is HeaderParameter }
        .map { p -> p as HeaderParameter }

    if (headerParams.isEmpty()) {
      headerGen = constant(HashMap())
    } else {
      val generators = headerParams
          .map { qp ->

            when (qp.type) {
              "string" ->
                propertyGen(qp.name, StringProperty())
              "integer"
              -> if (qp.format == "int64") {
                propertyGen(qp.name, LongProperty())
              } else {
                propertyGen(qp.name, IntegerProperty())
              }
              else -> throw NotImplementedException("unimplemented type " + qp.type)
            }

          }

      val sequence = sequence(generators)
      headerGen = sequence.map { l ->
        l.map { p -> p.name to p.value }.toMap()
      }
    }
    return headerGen
  }

  companion object {

    fun createTestCases(config: Config): List<TestCase> {

      val serverSpec = SwaggerParser().read(config.input)
      //    String swaggerString = Json.pretty(serverSpec);

      val fuzz = Fuzz(serverSpec.definitions)

      return serverSpec.paths.entries
          .flatMap { pe ->
            pe.value.operationMap.entries
                .flatMap { oe ->
                  val endpoint = pe.key
                  val method = oe.key
                  val params = oe.value.parameters
                  val bodyGen = fuzz.bodyParamsGenerator(params)
                  val queryGen = fuzz.queryParamsGenerator(params)
                  val pathGen = fuzz.pathParamsGenerator(params)
                  val headerGen = fuzz.headerParamsGenerator(params)
                  val formGen = fuzz.formParamsGenerator(params)

                  if (params.any { p ->
                    !(p is BodyParameter ||
                        p is QueryParameter ||
                        p is PathParameter ||
                        p is FormParameter ||
                        p is HeaderParameter)
                  }) {
                    throw NotImplementedException("need to handle other parameter types: " + params)
                  }

                  arrayListOf(
                      TestCase(config.host, endpoint, config.port, method, bodyGen, queryGen,
                          pathGen, headerGen, formGen)
                  )
                }
          }
    }
  }
}

