package com.glassthought.chainsaw.cli.sandbox

import com.glassthought.chainsaw.core.directLLMApi.ChatRequest
import com.glassthought.chainsaw.core.initializer.Initializer

suspend fun main(args: Array<String>) {
  val llm = Initializer.standard().initialize().glmDirectLLM


  llm.call(ChatRequest("Say hello in Russian")).also { response ->
    println("LLM response: ${response.text}")
  }
}