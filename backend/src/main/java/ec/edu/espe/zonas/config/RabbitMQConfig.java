package ec.edu.espe.zonas.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para el envío y recepción asíncrona de mensajes.
 */
@Configuration
public class RabbitMQConfig {

    public static final String QUEUE = "analisis.solicitudes";
    public static final String EXCHANGE = "analisis.exchange";
    public static final String ROUTING_KEY = "analisis.routingkey";

    /**
     * Define la cola donde se almacenarán las solicitudes de análisis de código.
     */
    @Bean
    public Queue queue() {
        return new Queue(QUEUE, true); // durable = true
    }

    /**
     * Define el intercambio (Exchange) de tipo Direct.
     */
    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE);
    }

    /**
     * Enlaza la cola con el intercambio a través de una clave de enrutamiento (routing key).
     */
    @Bean
    public Binding binding(Queue queue, DirectExchange exchange) {
        return BindingBuilder
                .bind(queue)
                .to(exchange)
                .with(ROUTING_KEY);
    }

    /**
     * Configura el conversor de mensajes para serializar y deserializar los payloads como JSON.
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Configura el RabbitTemplate personalizado para que use el conversor JSON.
     */
    @Bean
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }
}
