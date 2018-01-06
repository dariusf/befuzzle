package io.github.dariusf.befuzzle;

import com.fasterxml.jackson.core.JsonProcessingException
import com.mashape.unirest.http.ObjectMapper
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object Utility {

  val unirestJacksonOM: ObjectMapper = object : ObjectMapper {

    override fun <T> readValue(value: String, valueType: Class<T>): T {
      try {
        return Jackson.MAPPER.readValue(value, valueType)
      } catch (e: IOException) {
        throw RuntimeException(e)
      }
    }

    override fun writeValue(value: Any): String {
      try {
        return Jackson.MAPPER.writeValueAsString(value)
      } catch (e: JsonProcessingException) {
        throw RuntimeException(e)
      }

    }
  }

  fun nowISO8601(): String {
    return ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT)
  }
}
