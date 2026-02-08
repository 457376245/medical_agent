package com.medical.agent.infrastructure.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

  @Bean
  public DirectExchange agentExchange() {
    return new DirectExchange("agent.exchange.v1");
  }

  @Bean
  public Queue parseRequestQueue() {
    return new Queue("agent.parse.request.v1");
  }

  @Bean
  public Queue parseResultQueue() {
    return new Queue("agent.parse.result.v1");
  }

  @Bean
  public Queue generateRequestQueue() {
    return new Queue("agent.generate.request.v1");
  }

  @Bean
  public Queue generateResultQueue() {
    return new Queue("agent.generate.result.v1");
  }

  @Bean
  public Binding parseRequestBinding(
      DirectExchange agentExchange,
      @Qualifier("parseRequestQueue") Queue parseRequestQueue) {
    return BindingBuilder.bind(parseRequestQueue).to(agentExchange).with("agent.parse.request.v1");
  }

  @Bean
  public Binding parseResultBinding(
      DirectExchange agentExchange,
      @Qualifier("parseResultQueue") Queue parseResultQueue) {
    return BindingBuilder.bind(parseResultQueue).to(agentExchange).with("agent.parse.result.v1");
  }

  @Bean
  public Binding generateRequestBinding(
      DirectExchange agentExchange,
      @Qualifier("generateRequestQueue") Queue generateRequestQueue) {
    return BindingBuilder.bind(generateRequestQueue).to(agentExchange).with("agent.generate.request.v1");
  }

  @Bean
  public Binding generateResultBinding(
      DirectExchange agentExchange,
      @Qualifier("generateResultQueue") Queue generateResultQueue) {
    return BindingBuilder.bind(generateResultQueue).to(agentExchange).with("agent.generate.result.v1");
  }
}
