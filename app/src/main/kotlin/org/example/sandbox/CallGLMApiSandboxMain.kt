package org.example.sandbox

import com.glassthought.directLLMApi.ChatRequest
import com.glassthought.initializer.Initializer

suspend fun main(args: Array<String>) {
  val llm = Initializer.standard().initialize().glmDirectLLM


  llm.call(ChatRequest("Say hello in Russian")).also { response ->
    println("LLM response: ${response.text}")
  }
}