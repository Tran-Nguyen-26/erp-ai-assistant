package vn.edu.uet.erpai.rule.review;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import vn.edu.uet.erpai.config.AmqpConfig;

@Component
public class ReviewBatchProducer {

  private final RabbitTemplate rabbit;

  public ReviewBatchProducer(RabbitTemplate rabbit) {
    this.rabbit = rabbit;
  }

  public void publish(ReviewBatchMessage message) {
    rabbit.convertAndSend(AmqpConfig.EXCHANGE, AmqpConfig.BATCH_KEY, message);
  }
}
