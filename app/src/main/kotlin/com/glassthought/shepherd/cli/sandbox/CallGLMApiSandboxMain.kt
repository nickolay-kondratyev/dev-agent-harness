package com.glassthought.shepherd.cli.sandbox

import com.asgard.core.out.impl.console.SimpleConsoleOutFactory
import com.glassthought.shepherd.core.supporting.directLLMApi.ChatRequest
import com.glassthought.shepherd.core.initializer.ContextInitializer

suspend fun main(args: Array<String>) {
  val llm = ContextInitializer.standard().initialize(outFactory = SimpleConsoleOutFactory.standard()).infra.directLlm.budgetHigh


  llm.call(ChatRequest("Say hello in Russian")).also { response ->
    println("LLM response: ${response.text}")
  }
}