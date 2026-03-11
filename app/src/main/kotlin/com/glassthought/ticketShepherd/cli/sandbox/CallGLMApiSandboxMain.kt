package com.glassthought.ticketShepherd.cli.sandbox

import com.asgard.core.out.impl.console.SimpleConsoleOutFactory
import com.glassthought.ticketShepherd.core.supporting.directLLMApi.ChatRequest
import com.glassthought.ticketShepherd.core.initializer.Initializer

suspend fun main(args: Array<String>) {
  val llm = Initializer.standard().initialize(outFactory = SimpleConsoleOutFactory.standard()).infra.directLlm.glmDirectLLM


  llm.call(ChatRequest("Say hello in Russian")).also { response ->
    println("LLM response: ${response.text}")
  }
}