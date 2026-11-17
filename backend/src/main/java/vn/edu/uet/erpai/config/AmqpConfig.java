package vn.edu.uet.erpai.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Hàng đợi rà soát hàng loạt. Message lỗi sau 3 lần retry được chuyển vào DLQ. */
@Configuration
public class AmqpConfig {

  public static final String EXCHANGE = "review.exchange";
  public static final String DLX = "review.dlx";
  public static final String BATCH_QUEUE = "review.batch";
  public static final String BATCH_KEY = "review.batch";
  public static final String DLQ = "review.batch.dlq";

  @Bean
  DirectExchange reviewExchange() {
    return new DirectExchange(EXCHANGE);
  }

  @Bean
  DirectExchange reviewDeadLetterExchange() {
    return new DirectExchange(DLX);
  }

  @Bean
  Queue reviewBatchQueue() {
    return QueueBuilder.durable(BATCH_QUEUE)
        .withArgument("x-dead-letter-exchange", DLX)
        .withArgument("x-dead-letter-routing-key", DLQ)
        .build();
  }

  @Bean
  Queue reviewDeadLetterQueue() {
    return QueueBuilder.durable(DLQ).build();
  }

  @Bean
  Binding reviewBatchBinding() {
    return BindingBuilder.bind(reviewBatchQueue()).to(reviewExchange()).with(BATCH_KEY);
  }

  @Bean
  Binding reviewDeadLetterBinding() {
    return BindingBuilder.bind(reviewDeadLetterQueue()).to(reviewDeadLetterExchange()).with(DLQ);
  }

  @Bean
  MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
