package io.github.dariusf.befuzzle

import io.swagger.models.HttpMethod

/**
 * A possible test case execution
 */
data class PlanTest(val method: HttpMethod,
                    val endpoint: String,
                    val examples: Int)
