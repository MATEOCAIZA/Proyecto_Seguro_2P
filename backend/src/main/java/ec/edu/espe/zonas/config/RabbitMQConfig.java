package ec.edu.espe.zonas.config;

import ec.edu.espe.zonas.dtos.AnalisisRequestDto;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Configuración de RabbitMQ para el envío y recepción asíncrona de mensajes.
 *
 * Medidas de seguridad aplicadas:
 * - ClassMapper con lista blanca (whitelist) de tipos permitidos para
 *   deserialización JSON: previene ataques de tipo Gadget / RCE via
 *   deserialización arbitraria de clases (CWE-502).
 * - Publisher confirms habilitado: garantiza entrega acknowledgment.
 * - Mandatory messages: evita pérdida silenciosa si no hay cola enlazada.
 */
@Configuration
public class RabbitMQConfig {

    public static final String QUEUE       = "analisis.solicitudes";
    public static final String EXCHANGE    = "analisis.exchange";
    public static final String ROUTING_KEY = "analisis.routingkey";

    /**
     * Define la cola donde se almacenarán las solicitudes de análisis de código.
     * durable=true asegura persistencia ante reinicios del broker.
     */
    @Bean
    public Queue queue() {
        return new Queue(QUEUE, true);
    }

    /**
     * Define el intercambio (Exchange) de tipo Direct.
     */
    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE);
    }

    /**
     * Enlaza la cola con el intercambio a través de una clave de enrutamiento.
     */
    @Bean
    public Binding binding(Queue queue, DirectExchange exchange) {
        return BindingBuilder
                .bind(queue)
                .to(exchange)
                .with(ROUTING_KEY);
    }

    /**
     * ClassMapper con lista blanca estricta de tipos permitidos.
     * Solo se acepta AnalisisRequestDto como tipo deserializable,
     * bloqueando cualquier otra clase arbitraria (previene CWE-502).
     */
    @Bean
    public DefaultClassMapper classMapper() {
        DefaultClassMapper mapper = new DefaultClassMapper();
        mapper.setIdClassMapping(Map.of(
                "analisisRequest", AnalisisRequestDto.class
        ));
        mapper.setTrustedPackages("ec.edu.espe.zonas.dtos");
        return mapper;
    }

    /**
     * Conversor JSON con ClassMapper seguro.
     * No se delega la resolución de tipos al header __TypeId__ enviado
     * por el cliente; el mapeo es fijo y controlado en servidor.
     */
    @Bean
    public MessageConverter messageConverter(DefaultClassMapper classMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setClassMapper(classMapper);
        return converter;
    }

    /**
     * RabbitTemplate con publisher confirms y mandatory habilitado.
     * - confirmCallback: registra en log si el broker no recibió el mensaje.
     * - returnsCallback: registra mensajes no enrutados.
     */
    @Bean
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory,
                                     MessageConverter messageConverter) {
        // Habilitar publisher confirms a nivel de ConnectionFactory
        if (connectionFactory instanceof CachingConnectionFactory ccf) {
            ccf.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
            ccf.setPublisherReturns(true);
        }

        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setMandatory(true);

        // Log si el broker no confirma recepción del mensaje
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                org.slf4j.LoggerFactory.getLogger(RabbitMQConfig.class)
                        .error("Mensaje NO confirmado por el broker RabbitMQ. Causa: {}", cause);
            }
        });

        // Log si el mensaje no pudo ser enrutado a ninguna cola
        rabbitTemplate.setReturnsCallback(returned ->
                org.slf4j.LoggerFactory.getLogger(RabbitMQConfig.class)
                        .warn("Mensaje devuelto sin enrutar — exchange: {}, routingKey: {}, replyCode: {}",
                                returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode())
        );

        return rabbitTemplate;
    }
}
