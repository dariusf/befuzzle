package io.github.dariusf.befuzzle

import com.fasterxml.jackson.databind.JsonNode
import io.github.dariusf.befuzzle.Traversable.sequence
import io.swagger.models.ArrayModel
import io.swagger.models.Model
import io.swagger.models.ModelImpl
import io.swagger.models.RefModel
import io.swagger.models.parameters.BodyParameter
import io.swagger.models.parameters.FormParameter
import io.swagger.models.parameters.HeaderParameter
import io.swagger.models.parameters.Parameter
import io.swagger.models.parameters.PathParameter
import io.swagger.models.parameters.QueryParameter
import io.swagger.models.properties.ArrayProperty
import io.swagger.models.properties.BooleanProperty
import io.swagger.models.properties.DateTimeProperty
import io.swagger.models.properties.DecimalProperty
import io.swagger.models.properties.DoubleProperty
import io.swagger.models.properties.FloatProperty
import io.swagger.models.properties.IntegerProperty
import io.swagger.models.properties.LongProperty
import io.swagger.models.properties.MapProperty
import io.swagger.models.properties.ObjectProperty
import io.swagger.models.properties.Property
import io.swagger.models.properties.RefProperty
import io.swagger.models.properties.StringProperty
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

  private var definitions: Map<String, Gen<Any>>

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
  ) : Gen<Prop<Any>> {
    override fun generate(`in`: RandomnessSource): Prop<Any> {
      return Prop(key, definitions.getValue(definition).generate(`in`))
    }
  }

  /**
   * A named, concrete value that's been fully generated.
   */
  private class Prop<out T>(internal val name: String, internal val value: T)

  private fun propertyGen(name: String, prop: Property): Gen<Prop<Any>> {
    return propertyGen(prop)
        .map { o -> Prop(name, o) }
  }

  /**
   * Every property is nullable...
   */
  private fun propertyGen(prop: Property): Gen<Any> {
    return oneOf(
        constant("null") as Gen<Any>, // the null literal isn't allowed in generators
        propertyGenAux(prop))
  }

  private fun propertyGenAux(prop: Property): Gen<Any> {
    when (prop) {
      is LongProperty ->
        return if (prop.enum != null) {
          pick(prop.enum)
        } else {
          oneOf(longs().between(-3L, 3L),
              constant(Long.MAX_VALUE),
              constant(Long.MIN_VALUE))
        } as Gen<Any>
      is IntegerProperty ->
        return if (prop.enum != null) {
          pick(prop.enum)
        } else {
          oneOf(integers().between(-3, 3),
              constant(Int.MAX_VALUE),
              constant(Int.MIN_VALUE))
        } as Gen<Any>
      is FloatProperty ->
        return if (prop.enum != null) {
          pick(prop.enum)
        } else {
          oneOf(floats().between(-3f, 3f),
              constant(Float.MAX_VALUE),
              constant(Float.MIN_VALUE),
              constant(Float.NEGATIVE_INFINITY),
              constant(Float.POSITIVE_INFINITY),
              constant(Float.NaN))
        } as Gen<Any>
      is DoubleProperty ->
        return if (prop.enum != null) {
          pick(prop.enum)
        } else {
          oneOf(doubles().between(-3.0, 3.0),
              constant(Double.MAX_VALUE),
              constant(Double.MIN_VALUE),
              constant(Double.NEGATIVE_INFINITY),
              constant(Double.POSITIVE_INFINITY),
              constant(Double.NaN))
        } as Gen<Any>
      is DecimalProperty ->
        // Numbers with an unspecified format. The format property is
        // intended to be human-readable and is nullable, so it's not very
        // useful. We just try to reduce this into other cases we know about.
        return oneOf(
            propertyGen(IntegerProperty()), // one is nullable
            propertyGenAux(DoubleProperty()))
            as Gen<Any>
      is StringProperty ->
        // TODO check pattern
        return if (prop.enum != null) {
          pick(prop.enum)
        } else {
          val min = prop.minLength ?: 0
          val max = prop.maxLength ?: 5
          stringGen(min, max)
        } as Gen<Any>
      is BooleanProperty ->
        return Generate.booleans() as Gen<Any>
      is DateTimeProperty ->
        //      return dates().withMilliseconds(new Date().getTime())
        return constant<String>(Utility::nowISO8601) as Gen<Any>
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
        } as Gen<Any>
      }
      is MapProperty -> {
        val value = prop.additionalProperties
        val keys = stringGen(0, 5)
        val values = propertyGen(value)
        return maps().of(keys, values).ofSize(4)
            as Gen<Any>
      }
      is ObjectProperty -> {
        // TODO check how object works
        val keys = stringGen(0, 5)
        val values = stringGen(0, 5)
        return maps().of(keys, values).ofSize(4)
            as Gen<Any>
      }
      is RefProperty ->
        throw IllegalStateException("this should have been handled before this point")
      else ->
        throw NotImplementedException("unimplemented property type " + prop.javaClass.simpleName)
    }
  }

  private fun stringGen(min: Int, max: Int): Gen<String> {
    return oneOf(
        // This causes problems in URL processing, which isn't really the point of these tests
        // TODO we can add some Unicode characters later maybe
        // strings().allPossible().ofLengthBetween(min, max),
        strings().basicLatinAlphabet().ofLengthBetween(min, max))
  }

  fun modelGen(model: Model): Gen<Any> {

    when (model) {
      is ModelImpl -> {

        // May happen for interfaces
        if (model.getProperties() == null) {
          // TODO might want to pull this out
          return constant(mapOf<String, Any>())
        }
        val generators = model.properties.entries.map { (k, v) ->

          if (v is RefProperty) {
            dynamicGen(k, v)
          } else {
            propertyGen(k, v)
          }
        }

        val sequence: Gen<List<Prop<Any>>> = sequence(generators)
        return sequence.map { l -> l.map { k -> k.name to k.value }.toMap() }
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
      else -> throw NotImplementedException("need to handle other model types: $model")
    }
  }

  private fun dynamicGen(p: RefProperty): Gen<Any> {
    return dynamicGen("", p).map { x -> x.value }
  }

  private fun dynamicGen(key: String, p: RefProperty): Gen<Prop<Any>> {
    val name = p.simpleRef
    return DynamicGen(key, name)
  }

  private fun queryParamsGenerator(params: List<Parameter>): Gen<Map<String, String>> {
    val queryGen: Gen<Map<String, String>>
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
                "integer" ->
                  when (qp.format) {
                    "int64" -> propertyGen(qp.name, LongProperty())
                    else -> propertyGen(qp.name, IntegerProperty())
                  }
                "number" ->
                  when (qp.format) {
                    "double" -> propertyGen(qp.name, DoubleProperty())
                    else -> propertyGen(qp.name, DecimalProperty())
                  }
                else -> throw NotImplementedException("unimplemented type " + qp.type)
              }
            } else {
              propertyGen(qp.name, items)
            }

          }

      queryGen = sequence(generators).map { l ->
        l.map { p -> p.name to p.value.toString() }.toMap()
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
        modelGen(schema).map(Jackson::fromObject)
      }
      else -> null
    }
  }

  private fun pathParamsGenerator(params: List<Parameter>): Gen<Map<String, String>> {
    val pathGen: Gen<Map<String, String>>
    val pathParams = params
        .filter { p -> p is PathParameter }
        .map { p -> p as PathParameter }

    if (pathParams.isEmpty()) {
      pathGen = constant(HashMap())
    } else {
      val generators = pathParams.map { pp ->
        when (pp.type) {
          "string" ->
            propertyGen(pp.name, StringProperty())
          "integer"
          -> if (pp.format == "int64") {
            propertyGen(pp.name, LongProperty())
          } else {
            propertyGen(pp.name, IntegerProperty())
          }
          else -> throw NotImplementedException("unimplemented type " + pp.type)
        }
      }

      val sequence = sequence(generators)
      pathGen = sequence.map { l ->
        l.map { p -> p.name to p.value.toString() }.toMap()
      }
    }
    return pathGen
  }

  private fun formParamsGenerator(params: List<Parameter>): Gen<Map<String, String>> {
    val formGen: Gen<Map<String, String>>
    val formParams = params
        .filter { p -> p is FormParameter }
        .map { p -> p as FormParameter }

    if (formParams.isEmpty()) {
      formGen = constant(HashMap())
    } else {
      val generators = formParams.map { fp ->

        when (fp.type) {
          "string" ->
            propertyGen(fp.name, StringProperty())
          "integer"
          -> if (fp.format == "int64") {
            propertyGen(fp.name, LongProperty())
          } else {
            propertyGen(fp.name, IntegerProperty())
          }
          "file" ->
            // TODO bogus implementation; we might have to use a type other than JsonNode to create files
            propertyGen(fp.name, StringProperty())
          else -> throw NotImplementedException("unimplemented type " + fp.type)
        }

      }

      val sequence = sequence(generators)
      formGen = sequence.map { l ->
        l.map { p -> p.name to p.value.toString() }.toMap()
      }
    }
    return formGen
  }

  private fun headerParamsGenerator(params: List<Parameter>): Gen<Map<String, String>> {
    val headerGen: Gen<Map<String, String>>
    val headerParams = params
        .filter { p -> p is HeaderParameter }
        .map { p -> p as HeaderParameter }

    if (headerParams.isEmpty()) {
      headerGen = constant(HashMap())
    } else {
      val generators = headerParams
          .map { hp ->
            when (hp.type) {
              "string" ->
                // TODO header parameters must be ascii
                propertyGen(hp.name, StringProperty())
              "integer" ->
                when (hp.format) {
                  "int64" ->
                    propertyGen(hp.name, LongProperty())
                  else -> propertyGen(hp.name, IntegerProperty())
                }
              "array" -> propertyGen(hp.name, ArrayProperty(hp.items))
              else -> throw NotImplementedException("unimplemented type " + hp.type)
            }

          }

      val sequence = sequence(generators)
      headerGen = sequence.map { l ->
        l.map { p -> p.name to p.value.toString() }.toMap()
      }
    }
    return headerGen
  }

  companion object {

    fun createTestCases(config: Config): List<TestCase> {

      val serverSpec = SwaggerParser().read(config.specLocation)
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
                    throw NotImplementedException("need to handle other parameter types: $params")
                  }

                  val declaredResponses = oe.value.responses.keys
                      .filter { it != "default" }
                      .map(Integer::parseInt).toSet()

                  oe.value.responses.entries
                  arrayListOf(
                      TestCase(config, config.host, endpoint, config.port, method, bodyGen, queryGen,
                          pathGen, headerGen, formGen, declaredResponses)
                  )
                }
          }
    }
  }
}

