package io.github.dariusf.befuzzle

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.*

object Jackson {

  val MAPPER = ObjectMapper()

  fun fromObject(o: Any): JsonNode {
    return MAPPER.convertValue(o, JsonNode::class.java)
  }

  fun fromJsonMap(map: Map<String, JsonNode>): ObjectNode {
    val root = MAPPER.createObjectNode()
    map.forEach({ fieldName, value -> root.set(fieldName, value) })
    return root
  }

  fun fromMap(map: Map<String, Any>): ObjectNode {
    val root = MAPPER.createObjectNode()
    map.forEach { k, v -> root.set(k, fromObject(v)) }
    return root
  }

  fun fromList(map: List<JsonNode>): ArrayNode {
    val root = MAPPER.createArrayNode()
    map.forEach { v -> root.add(fromObject(v)) }
    return root
  }
}
