package com.medical.agent.infrastructure.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.agent.domain.vo.ParseRequestEvent;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ParseRequestPublisher {
  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;

  public ParseRequestPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
    this.rabbitTemplate = rabbitTemplate;
    this.objectMapper = objectMapper;
  }

  public void publish(ParseRequestEvent payload) {
    try {
      byte[] body = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
      MessageProperties properties = new MessageProperties();
      properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
      rabbitTemplate.send("agent.exchange.v1", "agent.parse.request.v1", new Message(body, properties));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Unable to serialize parse request payload", ex);
    }
  }
}
