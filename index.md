---
title: "JMS"
addon: "JMS"
repo: "https://github.com/seedstack/jms-addon"
author: Adrien LAUER
description: "Provides configuration, injection and connection resilience for Java Messaging System 1.1."
tags:
    - communication
    - transactions
zones:
    - Addons
noMenu: true    
---

Java Message Service (JMS) is a Java API that allows applications to create, send, receive, and read messages.
SeedStack JMS add-on provides a JMS 1.1 integration (JSR 914).<!--more--> 

It automatically manages connection factories, connections, sessions and message consumers/listeners while retaining the 
standard JMS API. It also transparently handles refresh of connections and sessions after a JMS failure.

## Dependency

{{< dependency g="org.seedstack.addons.jms" a="jms" >}}

{{% callout info %}}
JMS provider implementation is not provided by this add-on and must be configured depending on your messaging solution.
{{% /callout %}}

You may need to add the JMS specification dependency in modules that don't include the JMS provider:

{{< dependency g="javax.jms" a="jms-api" v="1.1-rev-1" s="provided" >}}

## Configuration

### Connection factories

{{% config p="jms.connectionsFactories" %}}
```yaml
jms:
  # Configured JMS connection factories with the name of the connection factory as key
  connectionFactories:
    connectionFactory1:
      # The fully qualified name of the provider ConnectionFactory implementation
      vendorClass: (Class<? extends ConnectionFactory>)

      # Bean properties that will be set on the provider ConnectionFactory implementation
      vendorProperties:
        beanProperty1: value1
      
      # The JNDI name of the connection factory (only when using JNDI, exclusive with vendorClass)  
      jndiName: (String)
      
      # The JNDI context where to lookup for the connection factory (only when using JNDI)  
      jndiContext: (String)
```
{{% /config %}}    

### Connections

{{% config p="jms.connections" %}}
```yaml
jms:
  # Configured JMS connections
  connections:
    connection1:
      # The name of the connection factory configured above to use
      connectionFactory: (String)
      
      # The client identifier to use for durable subscriptions (defaults to applicationId-connectionName if not specified)
      clientId: (String)
      
      # If true, the configured client id will be set on the connection  
      setClientId: (boolean)

      # The connection username if any
      user: (String)
      
      # The connection password if any 
      password: (String)
      
      # If true, the connection will attempt to reconnect automatically after a JMS failure  
      managed: (boolean)
      
      # The time in milliseconds to wait before trying the reconnect (in managed mode)
      reconnectionDelay: (int)

      # If true, JMS methods forbidden in a JEE environment will not be used
      jeeMode: (boolean)

      # The fully qualified name of the JMS exception listener class for this connection 
      exceptionListener: (Class<? extends ExceptionListener>)
      
      # The fully qualified name of the transaction exception handler for this connection
      exceptionHandler: (Class<? extends JmsExceptionHandler>)
```
{{% /config %}}  

### JEE mode

In a strict JEE environment, some JMS methods are forbidden (refer to the EE.6.7 section of the JavaEE platform specification).
When the JEE mode is enabled on a connection, the forbidden methods are not invoked. 

The major downside is that it prevents using asynchronous message reception. SeedStack will use message polling instead, 
so a message poller must be configured on the message listener when in JEE mode.

### Example

Assuming that we are using [Apache ActiveMQ](http://activemq.apache.org/), the following configuration will declare a
JMS connection named `connection1` using the connection factory `connectionFactory1`:

```yaml
jms:
  connectionFactories:
    connectionFactory1:
      vendorClass: org.apache.activemq.ActiveMQConnectionFactory
      vendorProperties:
        brokerURL: vm://localhost?broker.persistent=false
  connections:
    connection1:
      connectionFactory: connectionFactory1
```

## Sending messages

### Injecting a session 

The simplest way to send messages is to inject a {{< java "javax.jms.Session" >}}:

```java
public class SomeClass {
    @Inject
    private Session session;

    @Override
    @Transactional
    @JmsConnection("connection1")
    public void sendMessage() {
        Destination queue = session.createQueue("queue1");
        MessageProducer producer = session.createProducer(queue);
        TextMessage message = session.createTextMessage();
        message.setText(stringMessage);
        producer.send(message);
    }
}
```

{{% callout info %}}
The session object is managed by SeedStack, its usage is thread-safe and there is no need to close it. 
{{% /callout %}}

### Injecting a connection

For more control, a connection can be injected directly inject and used to manually create all needed objects to send a message.
In this case, only the connection is managed by SeedStack and you are responsible for properly closing the objects you
create:

```java
public class SomeClass {
    @Inject
    @Named("connection1")
    private Connection connection;

    public void sendMessage(String stringMessage) throws JMSException {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        try {
            Destination queue = session.createQueue("queue1");
            MessageProducer producer = session.createProducer(queue);
            TextMessage message = session.createTextMessage();
            message.setText(stringMessage);
            producer.send(message);
        } finally {
            session.close();
        }
    }
}
```

### Automatic reconnection

When you inject a session or a connection and managed mode is enabled for the connection, SeedStack will inject
managed objects which embed a reconnection mechanism. 

On such objects, creating a producer or sending a message when the connection is down will throw a {{< java "javax.jms.JMSException" >}}. 
When the connection is up again, objects are refreshed automatically and can be used again. Note that it is still up to you to 
handle the retry policy of the sent message.

## Receiving messages

To receive JMS messages, create a message listener class implementing the {{< java "javax.jms.MessageListener" >}} interface 
and annotated with {{< java "org.seedstack.jms.JmsMessageListener" "@" >}}:

```java
@JmsMessageListener(connection = "connection1", destinationName = "SOME.QUEUE")
public class SomeMessageListener implements MessageListener {
    @Logging
    private Logger logger;

    @Override
    @Transactional
    public void onMessage(Message message) {
        if (message instanceof TextMessage) {
            logger.info("Message received {}", ((TextMessage) message).getText());
        } else {
            logger.warn("Unsupported message type");
        }
    }
}
```

The {{< java "org.seedstack.jms.JmsMessageListener" "@" >}} annotation takes the following parameters:

* The `connection` parameter specifying the connection that will be used to receive the messages.
* The `destinationType` parameter specifying what kind of destination the class will listen to (queue or topic).
* The `destinationName` parameter specifying the name of the destination.
* The `poller` parameter is optional and is used to enable polling on this listener.

When a `poller` parameter is specified, no asynchronous message reception (i.e. driven by the JMS provider) is done. Message
polling is used instead.

{{% callout info %}}
Reception is automatically done in a transacted session if a {{< java "org.seedstack.seed.transaction.Transactional" "@" >}}
annotation is present on the `onMessage()` method or on the listener class.
{{% /callout %}}

### Simple message poller

If polling is needed on a particular listener you may use the {{< java "org.seedstack.jms.pollers.SimpleMessagePoller" >}} class for
basic polling needs. 

This poller spawns a thread which calls to `receive()` in a loop, dispatching the message to the `onMessage()` method when 
a message is received. If an exception occurs during `receive()`, the exception is dispatched to the connection exception 
listener if any.

If an exception is thrown during the reception or message handling, the polling thread is shutdown and scheduled to
restart 10 seconds later. When used in conjunction with the automatic reconnection, the exception also triggers a
connection refresh. In that case, the poller may retry to receive messages several times before the connection is up 
again, depending on the configured connection refresh timeout.

### Custom poller

A customer poller can be created by implementing the {{< java "org.seedstack.jms.MessagePoller" >}} interface:

```java
public class MyMessagePoller implements MessagePoller {
    private Session session;
    private MessageConsumer messageConsumer;
    private ExceptionListener exceptionListener;
    private MessageListener messageListener;

    @Override
    public void setSession(Session session) {
        this.session = session;
    }

    @Override
    public void setMessageConsumer(MessageConsumer messageConsumer) {
        this.messageConsumer = messageConsumer;
    }

    @Override
    public void setExceptionListener(ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
    }

    @Override
    public void setMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
    }

    @Override
    public synchronized void start() {
        // TODO: start receiving loop
    }

    @Override
    public synchronized void stop() {
        // TODO: stop receiving loop

    }
}
```
